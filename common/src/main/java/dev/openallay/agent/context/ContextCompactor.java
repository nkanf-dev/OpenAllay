package dev.openallay.agent.context;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelToolDefinition;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ContextCompactor {
    private static final Set<String> SUMMARY_FIELDS = Set.of(
            "goals", "preferences", "completedTopics", "currentTasks", "decisions",
            "unresolvedQuestions", "evidenceReferences");
    private static final String SUMMARY_SYSTEM = """
            Return one JSON object with string arrays goals, preferences, completedTopics, currentTasks,
            decisions, unresolvedQuestions, evidenceReferences. Do not invent facts, treat summaries as
            evidence, or include hidden reasoning. Remember Skill workflow names, but never claim
            summarized Skill document text is still present.
            """;
    private static final String DERIVED_PREFIX =
            "[OpenAllay derived conversation memory; NOT factual evidence]\n";

    public record Result(
            ContextProjection projection,
            ContextCheckpoint checkpoint,
            String failureCode,
            String failureMessage) {
        public Result {
            boolean success = projection != null;
            if (success == (failureCode != null || failureMessage != null)) {
                throw new IllegalArgumentException("compaction result success/failure is inconsistent");
            }
            if (!success && checkpoint == null) {
                throw new IllegalArgumentException("compaction failure requires a checkpoint");
            }
        }

        public boolean successful() {
            return projection != null;
        }
    }

    private final ModelClient model;
    private final Gson gson;
    private final ContextTokenEstimator estimator;
    private final ContextBudget budget;
    private final String modelIdentifier;
    private final Clock clock;

    public ContextCompactor(
            ModelClient model,
            Gson gson,
            ContextTokenEstimator estimator,
            ContextBudget budget,
            String modelIdentifier,
            Clock clock) {
        this.model = Objects.requireNonNull(model, "model");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.estimator = Objects.requireNonNull(estimator, "estimator");
        this.budget = Objects.requireNonNull(budget, "budget");
        if (modelIdentifier == null || modelIdentifier.isBlank()) {
            throw new IllegalArgumentException("modelIdentifier is required");
        }
        this.modelIdentifier = modelIdentifier;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletableFuture<Result> compact(
            String systemPrompt,
            List<ModelMessage> messages,
            int protectedFromIndex,
            List<ModelToolDefinition> tools,
            boolean stream,
            String schedulingKey,
            CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        List<ModelMessage> source = List.copyOf(messages);
        List<ModelToolDefinition> requestTools = List.copyOf(tools);
        List<ContextStructure.Unit> units = ContextStructure.units(source);
        ContextStructure.requireBoundary(units, protectedFromIndex, source.size());
        int originalEstimate = estimator.estimate(systemPrompt, source, requestTools);
        if (originalEstimate <= budget.inputTokens()) {
            return CompletableFuture.completedFuture(new Result(
                    new ContextProjection(source, ContextProjection.Kind.ORIGINAL, originalEstimate),
                    null, null, null));
        }
        Prefix prefix = summaryPrefix(source, protectedFromIndex, schedulingKey);
        if (prefix == null) {
            ContextCheckpoint failure = failedCheckpoint(
                    source, 0, Math.max(1, protectedFromIndex),
                    "summary_input_over_budget", "No structural summary prefix fits the model budget",
                    originalEstimate);
            return CompletableFuture.completedFuture(new Result(
                    null, failure, "context_compaction_failed", failure.failureMessage()));
        }
        ModelRequest summaryRequest = new ModelRequest(
                SUMMARY_SYSTEM,
                List.of(ModelMessage.userText(prefix.serialized())),
                List.of(),
                false,
                schedulingKey);
        return cancellation.observe(model.complete(summaryRequest, ignored -> {}, cancellation))
                .handle((turn, throwable) -> {
                    cancellation.throwIfCancelled();
                    if (throwable != null) {
                        Throwable cause = unwrap(throwable);
                        String code = cause instanceof ModelClientException modelFailure
                                ? modelFailure.failure().code() : "summary_failure";
                        ContextCheckpoint failure = failedCheckpoint(
                                source, 0, prefix.toIndexExclusive(), code,
                                safeMessage(cause), originalEstimate);
                        return new Result(null, failure, "context_compaction_failed",
                                "Context summary failed: " + code);
                    }
                    JsonObject summary;
                    try {
                        summary = parseSummary(turn.text());
                    } catch (RuntimeException malformed) {
                        ContextCheckpoint failure = failedCheckpoint(
                                source, 0, prefix.toIndexExclusive(),
                                "summary_malformed", "Summary response did not match schema",
                                originalEstimate);
                        return new Result(null, failure, "context_compaction_failed",
                                failure.failureMessage());
                    }
                    ArrayList<ModelMessage> projected = new ArrayList<>();
                    projected.add(ModelMessage.userText(DERIVED_PREFIX + summary));
                    projected.addAll(source.subList(prefix.toIndexExclusive(), source.size()));
                    int estimate = estimator.estimate(systemPrompt, projected, requestTools);
                    if (estimate > budget.inputTokens()) {
                        ContextCheckpoint failure = failedCheckpoint(
                                source, 0, prefix.toIndexExclusive(),
                                "summary_projection_over_budget",
                                "Summary projection still exceeds the model budget", estimate);
                        return new Result(null, failure, "context_compaction_failed",
                                failure.failureMessage());
                    }
                    String encoded = summary.toString();
                    ContextCheckpoint checkpoint = new ContextCheckpoint(
                            UUID.randomUUID(), 0, prefix.toIndexExclusive(),
                            ContextSourceHash.compute(
                                    gson, source.subList(0, prefix.toIndexExclusive())),
                            modelIdentifier, clock.instant(),
                            ContextCheckpoint.Status.SUCCEEDED, encoded, null, null, estimate);
                    return new Result(new ContextProjection(
                            projected, ContextProjection.Kind.SUMMARIZED, estimate),
                            checkpoint, null, null);
                });
    }

    public boolean matches(ContextCheckpoint checkpoint, List<ModelMessage> source) {
        if (checkpoint.status() != ContextCheckpoint.Status.SUCCEEDED
                || checkpoint.sourceToIndexExclusive() > source.size()) return false;
        return checkpoint.sourceHash().equals(ContextSourceHash.compute(gson, source.subList(
                checkpoint.sourceFromIndex(), checkpoint.sourceToIndexExclusive())));
    }

    public boolean requiresCompaction(
            String systemPrompt,
            List<ModelMessage> messages,
            List<ModelToolDefinition> tools) {
        return estimator.estimate(systemPrompt, messages, tools) > budget.inputTokens();
    }

    public Optional<ContextProjection> reuse(
            ContextCheckpoint checkpoint,
            String systemPrompt,
            List<ModelMessage> messages,
            int protectedFromIndex,
            List<ModelToolDefinition> tools) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        messages = List.copyOf(messages);
        if (checkpoint.status() != ContextCheckpoint.Status.SUCCEEDED
                || checkpoint.sourceFromIndex() != 0
                || checkpoint.sourceToIndexExclusive() > protectedFromIndex
                || !matches(checkpoint, messages.subList(0, protectedFromIndex))) {
            return Optional.empty();
        }
        JsonObject summary;
        try {
            List<ContextStructure.Unit> units = ContextStructure.units(messages);
            ContextStructure.requireBoundary(units, protectedFromIndex, messages.size());
            ContextStructure.requireBoundary(
                    units, checkpoint.sourceToIndexExclusive(), messages.size());
            summary = parseSummary(checkpoint.summary());
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
        ArrayList<ModelMessage> projected = new ArrayList<>();
        projected.add(ModelMessage.userText(DERIVED_PREFIX + summary));
        projected.addAll(messages.subList(
                checkpoint.sourceToIndexExclusive(), messages.size()));
        int estimate = estimator.estimate(systemPrompt, projected, tools);
        if (estimate > budget.inputTokens()) {
            return Optional.empty();
        }
        return Optional.of(new ContextProjection(
                projected, ContextProjection.Kind.SUMMARIZED, estimate));
    }

    private Prefix summaryPrefix(
            List<ModelMessage> messages,
            int protectedFromIndex,
            String schedulingKey) {
        List<ContextStructure.Unit> units = ContextStructure.units(messages);
        int bestEnd = -1;
        String best = null;
        for (ContextStructure.Unit unit : units) {
            if (unit.toIndexExclusive() > protectedFromIndex) break;
            List<ModelMessage> safe = ContextStructure.summarySafe(
                    messages.subList(0, unit.toIndexExclusive()));
            String serialized = gson.toJson(safe);
            ModelRequest request = new ModelRequest(
                    SUMMARY_SYSTEM, List.of(ModelMessage.userText(serialized)), List.of(), false,
                    schedulingKey);
            int estimate = estimator.estimate(
                    request.systemPrompt(), request.messages(), request.tools());
            if (estimate > budget.inputTokens()) break;
            bestEnd = unit.toIndexExclusive();
            best = serialized;
        }
        return bestEnd < 1 ? null : new Prefix(bestEnd, best);
    }

    private ContextCheckpoint failedCheckpoint(
            List<ModelMessage> messages, int from, int to, String code, String message, int estimate) {
        int safeTo = Math.min(Math.max(from + 1, to), messages.size());
        return new ContextCheckpoint(
                UUID.randomUUID(), from, safeTo,
                ContextSourceHash.compute(gson, messages.subList(from, safeTo)),
                modelIdentifier, clock.instant(),
                ContextCheckpoint.Status.FAILED, null, code, message, Math.max(0, estimate));
    }

    private static JsonObject parseSummary(String text) {
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(SUMMARY_FIELDS)) {
            throw new IllegalArgumentException("summary schema mismatch");
        }
        JsonObject object = parsed.getAsJsonObject();
        for (String field : SUMMARY_FIELDS) {
            if (!object.get(field).isJsonArray()) throw new IllegalArgumentException("summary field");
            for (JsonElement item : object.getAsJsonArray(field)) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("summary item");
                }
            }
        }
        return object.deepCopy();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private record Prefix(int toIndexExclusive, String serialized) {}
}
