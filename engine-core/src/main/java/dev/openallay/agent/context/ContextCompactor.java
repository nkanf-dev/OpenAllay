package dev.openallay.agent.context;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.model.ModelToolDefinition;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** One configured budget owns every active projection and summary request. */
public final class ContextCompactor {
    private static final List<String> SUMMARY_FIELD_ORDER = List.of(
            "goals", "preferences", "completedTopics", "currentTasks", "decisions",
            "unresolvedQuestions", "evidenceReferences");
    private static final Set<String> SUMMARY_FIELDS = Set.copyOf(SUMMARY_FIELD_ORDER);
    private static final String SUMMARY_SYSTEM = """
            Return one JSON object with string arrays goals, preferences, completedTopics, currentTasks,
            decisions, unresolvedQuestions, evidenceReferences. Do not invent facts, treat summaries as
            evidence, or include hidden reasoning. Remember Skill workflow names, but never claim
            summarized Skill document text is still present.
            """;
    private static final String DERIVED_PREFIX =
            "[OpenAllay derived conversation memory; NOT factual evidence]\n";
    // This is the instruction index's canonical unavailable view, so refresh is idempotent.
    private static final String RETIRED_SKILL = "skill_instructions: invalidated\n"
            + "projection_note: prior Skill plaintext is unavailable in the current catalog/context; "
            + "the historical Tool result status is unchanged.";
    private static final int MINIMUM_RESULT_BYTES = 256;

    public record Result(ContextProjection projection, ContextCheckpoint checkpoint,
                         String failureCode, String failureMessage) {
        public Result {
            boolean success = projection != null;
            if (success == (failureCode != null || failureMessage != null)) {
                throw new IllegalArgumentException("compaction result success/failure is inconsistent");
            }
            if (!success && checkpoint == null) {
                throw new IllegalArgumentException("compaction failure requires a checkpoint");
            }
        }
        public boolean successful() { return projection != null; }
    }

    private final ModelClient model;
    private final Gson gson;
    private final ContextTokenEstimator estimator;
    private final ContextBudget budget;
    private final String modelIdentifier;
    private final Clock clock;

    public ContextCompactor(ModelClient model, Gson gson, ContextTokenEstimator estimator,
                            ContextBudget budget, String modelIdentifier, Clock clock) {
        Objects.requireNonNull(model, "model");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.estimator = Objects.requireNonNull(estimator, "estimator");
        this.budget = Objects.requireNonNull(budget, "budget");
        if (modelIdentifier == null || modelIdentifier.isBlank()) {
            throw new IllegalArgumentException("modelIdentifier is required");
        }
        this.modelIdentifier = modelIdentifier;
        this.model = dev.openallay.model.ObservingModelClient.observe(model, modelIdentifier);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int estimateTokens(String systemPrompt, List<ModelMessage> messages,
                              List<ModelToolDefinition> tools) {
        return estimator.estimate(systemPrompt, messages, tools);
    }

    public ContextTokenEstimator estimator() { return estimator; }

    /** Prepare opaque restored data before model admission, without touching diagnostics. */
    public List<ModelMessage> prepareModelView(List<ModelMessage> messages) {
        ResultValues values = new ResultValues(messages);
        List<ModelMessage> receipts = boundResults(messages, MINIMUM_RESULT_BYTES, false, List.of(), values);
        configureResultPlan(values, messages, receipts, List.of());
        if (withinResultPlan(messages, values)) return List.copyOf(messages);
        ArrayList<ModelMessage> projected = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            ModelMessage message = messages.get(index);
            ArrayList<ModelContent> content = new ArrayList<>();
            for (int item = 0; item < message.content().size(); item++) {
                ModelContent original = message.content().get(item);
                if (original instanceof ModelContent.ToolResult result
                        && values.plannedTokens.containsKey(result.toolUseId())
                        && estimator.estimateText(modelText(result.value()))
                                > values.plannedTokens.get(result.toolUseId())) {
                    content.add(receipts.get(index).content().get(item));
                } else content.add(original);
            }
            projected.add(content.equals(message.content()) ? message : new ModelMessage(message.role(), content, message.inputObservation()));
        }
        return List.copyOf(projected);
    }

    public int inputTokenBudget() { return budget.inputTokens(); }

    public boolean requiresCompaction(String systemPrompt, List<ModelMessage> messages,
                                      List<ModelToolDefinition> tools) {
        ResultValues values = new ResultValues(messages);
        configureResultPlan(values, messages,
                boundResults(messages, MINIMUM_RESULT_BYTES, false, List.of(), values), List.of());
        if (!withinResultPlan(messages, values)) return true;
        return estimateTokens(systemPrompt, messages, tools) > inputTokenBudget();
    }

    /**
     * Fit complete successful result values together. IDs, arguments, current questions and real
     * error values stay exact. Encoded bytes are only a search parameter; the injected tokenizer
     * measures each whole candidate request. No token-to-byte ratio is assumed.
     */
    public Optional<ContextProjection> fitResults(String systemPrompt, List<ModelMessage> messages,
                                                  List<ModelToolDefinition> tools) {
        return fitResults(systemPrompt, messages, tools, List.of());
    }

    /** Fresh results still have a structured normalized view; use it instead of re-clipping text. */
    public Optional<ContextProjection> fitResults(String systemPrompt, List<ModelMessage> messages,
            List<ModelToolDefinition> tools, List<AgentToolResult> freshResults) {
        return fitResults(ignored -> systemPrompt, messages, tools, freshResults);
    }

    /** Derived metadata is recomputed from every candidate, not reserved under a stale loaded flag. */
    public Optional<ContextProjection> fitResults(
            java.util.function.Function<List<ModelMessage>, String> promptForProjection,
            List<ModelMessage> messages, List<ModelToolDefinition> tools,
            List<AgentToolResult> freshResults) {
        ContextStructure.units(messages);
        if (!freshResults.isEmpty() && (messages.isEmpty()
                || messages.get(messages.size() - 1).content().size() != freshResults.size())) {
            throw new IllegalArgumentException("Fresh results do not match the completed exchange");
        }
        ResultValues values = new ResultValues(messages);
        List<ModelMessage> receipts = boundResults(messages, MINIMUM_RESULT_BYTES, false, freshResults, values);
        configureResultPlan(values, messages, receipts, freshResults);
        if (freshResults.isEmpty() && withinResultPlan(messages, values)) {
            List<ModelMessage> safe = ModelContextCodec.safe(messages);
            int original = estimateProjection(promptForProjection, safe, tools);
            if (original <= inputTokenBudget()) return Optional.of(
                    new ContextProjection(safe, ContextProjection.Kind.ORIGINAL, original));
        }
        for (AgentToolResult result : freshResults) if (!result.failure()) {
            values.maximumBytes = Math.max(values.maximumBytes, result.projectionSizeUpperBound());
        }
        // Token counts are not monotonic in encoded bytes. Probe a finite geometric set before
        // refining a known fitting candidate, and never treat a failed minimum as a proof by itself.
        for (boolean retireSkills : new boolean[] {false, true}) {
            List<ModelMessage> best = null;
            int bestEstimate = Integer.MAX_VALUE;
            int fittingCap = -1;
            int upperCap = values.maximumBytes;
            int probeCap = upperCap;
            while (true) {
                List<ModelMessage> candidate = boundResults(messages, probeCap, retireSkills, freshResults, values);
                boolean withinPlan = withinResultPlan(candidate, values);
                int estimate = withinPlan ? estimateProjection(promptForProjection, candidate, tools) : Integer.MAX_VALUE;
                if (estimate <= inputTokenBudget()) {
                    best = candidate;
                    bestEstimate = estimate;
                    fittingCap = probeCap;
                    break;
                }
                if (probeCap == MINIMUM_RESULT_BYTES) break;
                upperCap = probeCap - 1;
                probeCap = Math.max(MINIMUM_RESULT_BYTES, probeCap / 2);
            }
            if (best == null) continue;
            int low = fittingCap;
            int high = upperCap;
            while (low < high) {
                int candidateCap = low + (int) (((long) high - low + 1L) / 2L);
                List<ModelMessage> candidate = boundResults(messages, candidateCap, retireSkills, freshResults, values);
                boolean withinPlan = withinResultPlan(candidate, values);
                int candidateEstimate = withinPlan ? estimateProjection(promptForProjection, candidate, tools) : Integer.MAX_VALUE;
                if (candidateEstimate <= inputTokenBudget()) {
                    low = candidateCap;
                    best = candidate;
                    bestEstimate = candidateEstimate;
                } else high = candidateCap - 1;
            }
            // Preserve actual result text. Only provider-private reasoning is excluded.
            best = ModelContextCodec.safe(best);
            bestEstimate = estimateProjection(promptForProjection, best, tools);
            if (bestEstimate <= inputTokenBudget() && withinResultPlan(best, values)) return Optional.of(
                    new ContextProjection(best, ContextProjection.Kind.BOUNDED, bestEstimate));
        }
        return Optional.empty();
    }

    /**
     * Capacity is an admission ceiling, not a target. Data views share the actual configured
     * next-completion planning budget. Their irreducible status/schema/handle receipt is measured
     * separately, so a small output setting never forces a fabricated empty success.
     */
    private void configureResultPlan(ResultValues values, List<ModelMessage> source,
            List<ModelMessage> receipts, List<AgentToolResult> freshResults) {
        int count = 0;
        if (!freshResults.isEmpty()) {
            for (ModelContent item : source.get(source.size() - 1).content()) {
                if (item instanceof ModelContent.ToolResult result && !result.error()
                        && !values.instructions.contains(result)) count++;
            }
        }
        int currentShare = Math.max(1, budget.maxOutputTokens() / Math.max(1, count));
        for (int index = 0; index < source.size(); index++) {
            int share = !freshResults.isEmpty() && index == source.size() - 1
                    ? currentShare : budget.maxOutputTokens();
            List<ModelContent> originals = source.get(index).content();
            List<ModelContent> minimums = receipts.get(index).content();
            for (int item = 0; item < originals.size(); item++) {
                if (originals.get(item) instanceof ModelContent.ToolResult result && !result.error()
                        && !values.instructions.contains(result)) {
                    ModelContent.ToolResult minimum = (ModelContent.ToolResult) minimums.get(item);
                    int floor = estimator.estimateText(modelText(minimum.value()));
                    values.plannedTokens.put(result.toolUseId(), (int) Math.min(Integer.MAX_VALUE,
                            (long) floor + share));
                }
            }
        }
    }

    private boolean withinResultPlan(List<ModelMessage> candidate, ResultValues values) {
        for (ModelMessage message : candidate) for (ModelContent item : message.content()) {
            if (item instanceof ModelContent.ToolResult result && values.plannedTokens.containsKey(result.toolUseId())
                    && estimator.estimateText(modelText(result.value()))
                            > values.plannedTokens.get(result.toolUseId())) return false;
        }
        return true;
    }

    private static String modelText(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : value.toString();
    }

    private int estimateProjection(java.util.function.Function<List<ModelMessage>, String> prompt,
            List<ModelMessage> messages, List<ModelToolDefinition> tools) {
        List<ModelMessage> safe = ModelContextCodec.safe(messages);
        return estimateTokens(prompt.apply(safe), safe, tools);
    }

    public CompletableFuture<Result> compact(String systemPrompt, List<ModelMessage> messages,
            int protectedFromIndex, List<ModelToolDefinition> tools, boolean stream,
            String schedulingKey, CancellationSignal cancellation) {
        return compact(ignored -> systemPrompt, messages, protectedFromIndex, tools, stream,
                schedulingKey, cancellation, ImagePayloadResolver.unavailable());
    }

    public CompletableFuture<Result> compact(String systemPrompt, List<ModelMessage> messages,
            int protectedFromIndex, List<ModelToolDefinition> tools, boolean stream,
            String schedulingKey, CancellationSignal cancellation, ImagePayloadResolver images) {
        return compact(ignored -> systemPrompt, messages, protectedFromIndex, tools, stream,
                schedulingKey, cancellation, images);
    }

    public CompletableFuture<Result> compact(
            java.util.function.Function<List<ModelMessage>, String> promptForProjection,
            List<ModelMessage> messages, int protectedFromIndex, List<ModelToolDefinition> tools,
            boolean stream, String schedulingKey, CancellationSignal cancellation) {
        return compact(promptForProjection, messages, protectedFromIndex, tools, stream,
                schedulingKey, cancellation, ImagePayloadResolver.unavailable());
    }

    public CompletableFuture<Result> compact(
            java.util.function.Function<List<ModelMessage>, String> promptForProjection,
            List<ModelMessage> messages, int protectedFromIndex, List<ModelToolDefinition> tools,
            boolean stream, String schedulingKey, CancellationSignal cancellation,
            ImagePayloadResolver images) {
        return compact(promptForProjection, messages, protectedFromIndex, tools, stream,
                schedulingKey, cancellation, images, ignored -> {});
    }

    /** The observer belongs to this exact request, including all summary chunks and retries. */
    public CompletableFuture<Result> compact(
            java.util.function.Function<List<ModelMessage>, String> promptForProjection,
            List<ModelMessage> messages, int protectedFromIndex, List<ModelToolDefinition> tools,
            boolean stream, String schedulingKey, CancellationSignal cancellation,
            Consumer<ModelEvent> usageObserver) {
        return compact(promptForProjection, messages, protectedFromIndex, tools, stream,
                schedulingKey, cancellation, ImagePayloadResolver.unavailable(), usageObserver);
    }

    /** Scoped images and actual-call usage belong to this exact request and all its summary retries. */
    public CompletableFuture<Result> compact(
            java.util.function.Function<List<ModelMessage>, String> promptForProjection,
            List<ModelMessage> messages, int protectedFromIndex, List<ModelToolDefinition> tools,
            boolean stream, String schedulingKey, CancellationSignal cancellation,
            ImagePayloadResolver images, Consumer<ModelEvent> usageObserver) {
        Objects.requireNonNull(images, "images");
        Objects.requireNonNull(usageObserver, "usageObserver");
        cancellation.throwIfCancelled();
        List<ModelMessage> source = List.copyOf(messages);
        List<ModelToolDefinition> requestTools = List.copyOf(tools);
        List<ContextStructure.Unit> units = ContextStructure.units(source);
        ContextStructure.requireBoundary(units, protectedFromIndex, source.size());
        Optional<ContextProjection> fitted = fitResults(promptForProjection, source, requestTools, List.of());
        if (fitted.isPresent()) return CompletableFuture.completedFuture(
                new Result(fitted.orElseThrow(), null, null, null));

        List<ModelMessage> minimum = ModelContextCodec.safe(boundResults(source, MINIMUM_RESULT_BYTES, true));
        int originalEstimate = estimateProjection(promptForProjection, minimum, requestTools);
        JsonObject empty = emptySummary();
        int prefixEnd = -1;
        int summaryTarget = 0;
        for (ContextStructure.Unit unit : units) {
            if (unit.toIndexExclusive() > protectedFromIndex) break;
            List<ModelMessage> candidate = summarized(empty,
                    imageBlocks(minimum.subList(0, unit.toIndexExclusive())),
                    minimum.subList(unit.toIndexExclusive(), minimum.size()));
            int estimate = estimateProjection(promptForProjection, candidate, requestTools);
            if (estimate <= inputTokenBudget()) {
                prefixEnd = unit.toIndexExclusive();
                summaryTarget = Math.min(budget.maxOutputTokens(), Math.max(1,
                        inputTokenBudget() - estimate + estimator.estimateText(empty.toString())));
                // The shortest sufficient prefix preserves the most recent exact history.
                break;
            }
        }
        if (prefixEnd < 1) return CompletableFuture.completedFuture(failure(source,
                Math.max(1, protectedFromIndex), "fixed_context_over_budget",
                "Protected question, tool arguments, errors and minimum result projections exceed "
                        + "the configured model input budget", originalEstimate));
        List<ModelMessage> suffix = List.copyOf(minimum.subList(prefixEnd, minimum.size()));
        return compactPrefix(promptForProjection, source, prefixEnd,
                minimum.subList(0, prefixEnd), suffix, requestTools, summaryTarget,
                schedulingKey, cancellation, images, usageObserver, originalEstimate, false);
    }

    private CompletableFuture<Result> compactPrefix(
            java.util.function.Function<List<ModelMessage>, String> promptForProjection,
            List<ModelMessage> source, int end, List<ModelMessage> history, List<ModelMessage> suffix,
            List<ModelToolDefinition> requestTools, int target, String schedulingKey,
            CancellationSignal cancellation, ImagePayloadResolver images,
            Consumer<ModelEvent> usageObserver, int originalEstimate, boolean manual) {
        List<ModelContent> retainedImages = imageBlocks(history);
        List<ContextStructure.Unit> historyUnits = ContextStructure.units(history);
        List<String> serializedUnits = historyUnits.stream().map(unit ->
                gson.toJson(summarySource(unit.messages()))).toList();
        return summarizeChunks(historyUnits, serializedUnits, 0, null, target, schedulingKey, cancellation,
                        promptForProjection, retainedImages, suffix, requestTools, images, usageObserver, manual)
                .handle((summary, throwable) -> {
                    cancellation.throwIfCancelled();
                    if (throwable != null) {
                        Throwable cause = unwrap(throwable);
                        String code = cause instanceof ModelClientException modelFailure
                                ? modelFailure.failure().code() : "summary_failure";
                        return failure(source, end, code, safeMessage(cause), originalEstimate);
                    }
                    List<ModelMessage> projected = summarized(summary, retainedImages, suffix);
                    int estimate = estimateProjection(promptForProjection, projected, requestTools);
                    if (estimate > inputTokenBudget()) return failure(source, end,
                            "summary_output_over_budget", "Summary did not fit its admitted target", estimate);
                    cancellation.throwIfCancelled();
                    if (manual && estimate >= originalEstimate) {
                        return originalProjection(source, originalEstimate);
                    }
                    ContextCheckpoint checkpoint = new ContextCheckpoint(UUID.randomUUID(), 0, end,
                            ContextSourceHash.compute(gson, source.subList(0, end)), modelIdentifier,
                            clock.instant(), ContextCheckpoint.Status.SUCCEEDED, summary.toString(),
                            null, null, estimate);
                    return new Result(new ContextProjection(projected,
                            ContextProjection.Kind.SUMMARIZED, estimate), checkpoint, null, null);
                });
    }

    /** Force a real older-history summary even when the original request is already under budget. */
    public CompletableFuture<Result> compactManually(
            java.util.function.Function<List<ModelMessage>, String> promptForProjection,
            List<ModelMessage> messages, int protectedFromIndex, List<ModelToolDefinition> tools,
            String schedulingKey, CancellationSignal cancellation) {
        return compactManually(promptForProjection, messages, protectedFromIndex, tools,
                schedulingKey, cancellation, ImagePayloadResolver.unavailable(), ignored -> {});
    }

    public CompletableFuture<Result> compactManually(
            java.util.function.Function<List<ModelMessage>, String> promptForProjection,
            List<ModelMessage> messages, int protectedFromIndex, List<ModelToolDefinition> tools,
            String schedulingKey, CancellationSignal cancellation, ImagePayloadResolver images) {
        return compactManually(promptForProjection, messages, protectedFromIndex, tools,
                schedulingKey, cancellation, images, ignored -> {});
    }

    public CompletableFuture<Result> compactManually(
            java.util.function.Function<List<ModelMessage>, String> promptForProjection,
            List<ModelMessage> messages, int protectedFromIndex, List<ModelToolDefinition> tools,
            String schedulingKey, CancellationSignal cancellation, Consumer<ModelEvent> usageObserver) {
        return compactManually(promptForProjection, messages, protectedFromIndex, tools,
                schedulingKey, cancellation, ImagePayloadResolver.unavailable(), usageObserver);
    }

    /** Images remain native visual input; the exact recent suffix is never result-clipped. */
    public CompletableFuture<Result> compactManually(
            java.util.function.Function<List<ModelMessage>, String> promptForProjection,
            List<ModelMessage> messages, int protectedFromIndex, List<ModelToolDefinition> tools,
            String schedulingKey, CancellationSignal cancellation,
            ImagePayloadResolver images, Consumer<ModelEvent> usageObserver) {
        Objects.requireNonNull(promptForProjection, "promptForProjection");
        Objects.requireNonNull(images, "images");
        Objects.requireNonNull(usageObserver, "usageObserver");
        cancellation.throwIfCancelled();
        List<ModelMessage> source = List.copyOf(messages);
        List<ModelToolDefinition> requestTools = List.copyOf(tools);
        List<ContextStructure.Unit> units = ContextStructure.units(source);
        ContextStructure.requireBoundary(units, protectedFromIndex, source.size());
        List<ModelMessage> safe = ModelContextCodec.safe(source);
        int originalEstimate = estimateProjection(promptForProjection, safe, requestTools);
        int protectedEnd = protectedFromIndex;
        if (!units.isEmpty()) {
            // Always keep at least one complete unit and the latest real user turn, even if a
            // caller supplies the end of history. A derived-memory message is not a new turn.
            protectedEnd = Math.min(protectedEnd, units.get(units.size() - 1).fromIndex());
            for (int index = units.size() - 1; index >= 0; index--) {
                ContextStructure.Unit unit = units.get(index);
                ModelMessage first = unit.messages().get(0);
                if (!unit.toolExchange() && first.role() == ModelRole.USER && !derivedMemory(first)) {
                    protectedEnd = Math.min(protectedEnd, unit.fromIndex());
                    break;
                }
            }
        }
        int prefixEnd = -1;
        int summaryTarget = 0;
        boolean substantive = false;
        JsonObject empty = emptySummary();
        for (ContextStructure.Unit unit : units) {
            if (unit.toIndexExclusive() > protectedEnd) break;
            substantive |= substantive(unit);
            if (!substantive) continue;
            int end = unit.toIndexExclusive();
            List<ModelMessage> candidate = summarized(empty, imageBlocks(source.subList(0, end)),
                    ModelContextCodec.safe(source.subList(end, source.size())));
            int estimate = estimateProjection(promptForProjection, candidate, requestTools);
            if (estimate >= originalEstimate || estimate > inputTokenBudget()) continue;
            prefixEnd = end;
            summaryTarget = Math.min(budget.maxOutputTokens(), Math.max(1,
                    inputTokenBudget() - estimate + estimator.estimateText(empty.toString())));
            // The command summarizes all eligible older units, not just enough to admit a request.
        }
        if (prefixEnd < 1) return CompletableFuture.completedFuture(
                originalProjection(source, originalEstimate));
        List<ModelMessage> suffix = ModelContextCodec.safe(source.subList(prefixEnd, source.size()));
        List<ModelMessage> history = ModelContextCodec.safe(boundResults(
                source.subList(0, prefixEnd), MINIMUM_RESULT_BYTES, true));
        return compactPrefix(promptForProjection, source, prefixEnd, history, suffix, requestTools,
                summaryTarget, schedulingKey, cancellation, images, usageObserver, originalEstimate, true);
    }

    private static boolean derivedMemory(ModelMessage message) {
        return !message.content().isEmpty() && message.content().get(0) instanceof ModelContent.Text text
                && text.text().startsWith(DERIVED_PREFIX);
    }

    private static boolean substantive(ContextStructure.Unit unit) {
        for (ModelMessage message : unit.messages()) {
            if (derivedMemory(message)) continue;
            for (ModelContent content : message.content()) {
                if (content instanceof ModelContent.Text text && !text.text().isBlank()) return true;
                if (content instanceof ModelContent.ToolUse || content instanceof ModelContent.ToolResult
                        || content instanceof ModelContent.Image) return true;
            }
        }
        return false;
    }

    private Result originalProjection(List<ModelMessage> source, int estimate) {
        return new Result(new ContextProjection(ModelContextCodec.safe(source),
                ContextProjection.Kind.ORIGINAL, estimate), null, null, null);
    }

    /** Every iteration consumes complete units. Oversized output has one targeted retry only. */
    private CompletableFuture<JsonObject> summarizeChunks(List<ContextStructure.Unit> units,
            List<String> serialized, int from, JsonObject prior, int target, String schedulingKey,
            CancellationSignal cancellation,
            java.util.function.Function<List<ModelMessage>, String> finalPrompt,
            List<ModelContent> retainedImages, List<ModelMessage> suffix,
            List<ModelToolDefinition> tools, ImagePayloadResolver images,
            Consumer<ModelEvent> usageObserver, boolean enforceOutputLimit) {
        cancellation.throwIfCancelled();
        String summarySystem = SUMMARY_SYSTEM + "\nBudget: " + target + " output tokens.";
        // Additive text costs are a planning hint, never admission proof. Native token merges and
        // provider framing are measured on the selected full request below.
        long planned = estimateTokens(summarySystem, prior == null
                ? List.of(ModelMessage.userText("[]"))
                : List.of(summaryInput(DERIVED_PREFIX + prior, unitImages(units, 0, from)),
                        ModelMessage.userText("[]")), List.of());
        int next = from;
        while (next < units.size()) {
            int unitCost = estimator.estimateText(serialized.get(next));
            if (next > from && planned + unitCost > inputTokenBudget()) break;
            planned += unitCost;
            next++;
        }
        ModelRequest selected = null;
        while (next > from) {
            String payload = joinUnits(serialized.subList(from, next));
            ModelMessage sourceInput = summaryInput(payload, unitImages(units, from, next));
            List<ModelMessage> input = prior == null ? List.of(sourceInput)
                    : List.of(summaryInput(DERIVED_PREFIX + prior, unitImages(units, 0, from)), sourceInput);
            ModelRequest request = new ModelRequest(summarySystem, input, List.of(),
                    false, schedulingKey, target, images);
            if (estimateTokens(request.systemPrompt(), request.messages(), request.tools()) <= inputTokenBudget()) {
                selected = request;
                break;
            }
            next--;
        }
        if (selected == null) return CompletableFuture.failedFuture(new ModelClientException(
                new dev.openallay.model.ModelFailure("summary_source_unit_over_budget",
                        "A complete historical structural unit cannot fit a summary request", null)));
        final int following = next;
        final String nextPayload = following < units.size() ? serialized.get(following) : null;
        int carryTarget = target;
        if (nextPayload != null) {
            int minimumCarry = estimateTokens(summarySystem, List.of(
                    summaryInput(DERIVED_PREFIX + emptySummary(), unitImages(units, 0, following)),
                    summaryInput(nextPayload, unitImages(units, following, following + 1))), List.of());
            if (minimumCarry > inputTokenBudget()) return CompletableFuture.failedFuture(
                    new ModelClientException(new dev.openallay.model.ModelFailure(
                            "summary_carry_over_budget",
                            "The next structural unit cannot fit with minimum conversation memory", null)));
            carryTarget = Math.min(target, Math.max(1, inputTokenBudget() - minimumCarry
                    + estimator.estimateText(emptySummary().toString())));
        }
        final ModelRequest admitted = new ModelRequest(
                SUMMARY_SYSTEM + "\nBudget: " + carryTarget + " output tokens.", selected.messages(),
                selected.tools(), selected.stream(), selected.sessionKey(), carryTarget, selected.images());
        java.util.function.Predicate<JsonObject> fits = summary -> {
            if (estimateProjection(finalPrompt, summarized(summary, retainedImages, suffix), tools)
                    > inputTokenBudget()) {
                return false;
            }
            return nextPayload == null || estimateTokens(summarySystem, List.of(
                    summaryInput(DERIVED_PREFIX + summary, unitImages(units, 0, following)),
                    summaryInput(nextPayload, unitImages(units, following, following + 1))), List.of())
                    <= inputTokenBudget();
        };
        return summarizeAdmitted(admitted, cancellation, fits, false, usageObserver, enforceOutputLimit)
                .thenCompose(summary -> {
            cancellation.throwIfCancelled();
            if (following == units.size()) return CompletableFuture.completedFuture(summary);
            return summarizeChunks(units, serialized, following, summary, target, schedulingKey, cancellation,
                    finalPrompt, retainedImages, suffix, tools, images, usageObserver, enforceOutputLimit);
        });
    }

    /** Full anchors stay in durable original context; summary source gets only the concise projection. */
    private static List<ModelMessage> summarySource(List<ModelMessage> messages) {
        return ContextStructure.summarySafe(messages).stream().map(message -> {
            if (message.inputObservation().isEmpty()) return message;
            ArrayList<ModelContent> content = new ArrayList<>(message.content());
            content.add(new ModelContent.Text(dev.openallay.model.image.ModelImages.inputObservationLabel(
                    message.inputObservation().orElseThrow())));
            return new ModelMessage(message.role(), content);
        }).toList();
    }

    private static String joinUnits(List<String> serialized) {
        StringBuilder payload = new StringBuilder("[");
        for (String unit : serialized) {
            String entries = unit.substring(1, unit.length() - 1);
            if (!entries.isEmpty()) {
                if (payload.length() > 1) payload.append(',');
                payload.append(entries);
            }
        }
        return payload.append(']').toString();
    }

    private CompletableFuture<JsonObject> summarizeAdmitted(ModelRequest admitted,
            CancellationSignal cancellation, java.util.function.Predicate<JsonObject> fits,
            boolean targetedRetry, Consumer<ModelEvent> usageObserver, boolean enforceOutputLimit) {
        cancellation.throwIfCancelled();
        if (estimateTokens(admitted.systemPrompt(), admitted.messages(), admitted.tools())
                > inputTokenBudget()) return CompletableFuture.failedFuture(new ModelClientException(
                        new dev.openallay.model.ModelFailure("summary_input_over_budget",
                                "Final summary request exceeds the configured input budget", null)));
        return cancellation.observe(model.complete(admitted, event -> {
                    if (event instanceof ModelEvent.UsageObserved
                            || event instanceof ModelEvent.UsageStarted) usageObserver.accept(event);
                }, cancellation))
                .thenCompose(turn -> {
                    cancellation.throwIfCancelled();
                    JsonObject summary;
                    try { summary = parseSummary(turn.text()); }
                    catch (RuntimeException malformed) {
                        return CompletableFuture.failedFuture(new ModelClientException(
                                new dev.openallay.model.ModelFailure("summary_malformed",
                                        "Summary response did not match schema", null)));
                    }
                    if ((!enforceOutputLimit || estimator.estimateText(turn.text()) <= admitted.maxOutputTokens())
                            && fits.test(summary)) return CompletableFuture.completedFuture(summary);
                    if (targetedRetry) return CompletableFuture.failedFuture(new ModelClientException(
                            new dev.openallay.model.ModelFailure("summary_output_over_budget",
                                    "Summary exceeded its target after one bounded retry", null)));
                    // Never feed an overlong response back into the model. Re-read only the already
                    // admitted source with an explicit tighter instruction and the same output cap.
                    String prompt = "Return the seven JSON string arrays goals, preferences, completedTopics, "
                            + "currentTasks, decisions, unresolvedQuestions, evidenceReferences. "
                            + "Your previous memory exceeded its target. Use fewer, shorter items. "
                            + "Preserve unresolved work and actual errors; never invent evidence or "
                            + "claim omitted Skill plaintext is loaded. Output budget: "
                            + admitted.maxOutputTokens() + " tokens.";
                    ModelRequest retry = new ModelRequest(prompt, admitted.messages(),
                            List.of(), false, admitted.sessionKey(), admitted.maxOutputTokens(), admitted.images());
                    if (estimateTokens(retry.systemPrompt(), retry.messages(), retry.tools())
                            > inputTokenBudget()) return CompletableFuture.failedFuture(
                                    new ModelClientException(new dev.openallay.model.ModelFailure(
                                            "summary_retry_input_over_budget",
                                            "Targeted summary retry cannot fit the configured input budget", null)));
                    return summarizeAdmitted(retry, cancellation, fits, true, usageObserver, enforceOutputLimit);
                });
    }

    private static List<ModelMessage> summarized(JsonObject summary,
            List<ModelContent> images, List<ModelMessage> suffix) {
        ArrayList<ModelMessage> projected = new ArrayList<>();
        // Text may be derived memory, but retained images remain actual visual input.
        projected.add(summaryInput(DERIVED_PREFIX + summary, images));
        projected.addAll(suffix);
        return List.copyOf(projected);
    }

    private static ModelMessage summaryInput(String text, List<ModelContent> images) {
        ArrayList<ModelContent> content = new ArrayList<>();
        content.add(new ModelContent.Text(text));
        content.addAll(images);
        return new ModelMessage(ModelRole.USER, content);
    }

    /** Keep image occurrences and their order; reference equality is not deletion permission. */
    private static List<ModelContent> imageBlocks(List<ModelMessage> messages) {
        return dev.openallay.model.image.ModelImages.observationContent(messages);
    }

    private static List<ModelContent> unitImages(List<ContextStructure.Unit> units, int from, int to) {
        return imageBlocks(units.subList(from, to).stream()
                .flatMap(unit -> unit.messages().stream()).toList());
    }

    private static List<ModelMessage> boundResults(List<ModelMessage> messages, int cap,
                                                   boolean retireSkills) {
        return boundResults(messages, cap, retireSkills, List.of(), new ResultValues(messages));
    }

    private static List<ModelMessage> boundResults(List<ModelMessage> messages, int cap,
            boolean retireSkills, List<AgentToolResult> freshResults, ResultValues values) {
        ArrayList<ModelMessage> projected = new ArrayList<>();
        int messageIndex = 0;
        for (ModelMessage message : messages) {
            int resultIndex = 0;
            ArrayList<ModelContent> content = new ArrayList<>();
            for (ModelContent item : message.content()) {
                if (item instanceof ModelContent.ToolResult result && !result.error()) {
                    JsonElement value = values.values.get(result);
                    int originalBytes = values.encodedSizes.get(result);
                    boolean skill = values.instructions.contains(result);
                    if (skill) {
                        if (retireSkills && originalBytes > cap) value = new JsonPrimitive(RETIRED_SKILL);
                    } else if (messageIndex == messages.size() - 1 && !freshResults.isEmpty()) {
                        // A fresh result can expose a lazy canonical source. Its initial transport
                        // preview must not become a permanent information ceiling for admission.
                        value = freshResults.get(resultIndex).modelValue(cap);
                    } else if (originalBytes > cap) {
                        value = AgentToolResult.boundedModelValue(value, cap);
                    }
                    content.add(value == values.values.get(result) ? result
                            : new ModelContent.ToolResult(result.toolUseId(), value, result.error(), result.images()));
                } else content.add(item);
                if (item instanceof ModelContent.ToolResult) resultIndex++;
            }
            projected.add(content.equals(message.content()) ? message : new ModelMessage(message.role(), content, message.inputObservation()));
            messageIndex++;
        }
        return List.copyOf(projected);
    }

    /** Request-local immutable snapshots avoid repeated full result encoding/deep copies. */
    private static final class ResultValues {
        private final java.util.IdentityHashMap<ModelContent.ToolResult, JsonElement> values =
                new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<ModelContent.ToolResult, Integer> encodedSizes =
                new java.util.IdentityHashMap<>();
        private final java.util.Set<ModelContent.ToolResult> instructions =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private final java.util.Map<String, Integer> plannedTokens = new java.util.HashMap<>();
        private int maximumBytes = MINIMUM_RESULT_BYTES;

        private ResultValues(List<ModelMessage> messages) {
            java.util.Map<String, String> names = new java.util.HashMap<>();
            for (ModelMessage message : messages) for (ModelContent item : message.content()) {
                if (item instanceof ModelContent.ToolUse use) names.put(use.id(), use.name());
                if (item instanceof ModelContent.ToolResult result && !result.error()) {
                    JsonElement value = result.value();
                    long measuredBytes = dev.openallay.tool.result.JsonResultProjection.serializedBytes(value);
                    int size = measuredBytes >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) measuredBytes;
                    values.put(result, value);
                    encodedSizes.put(result, size);
                    String name = names.getOrDefault(result.toolUseId(), "").toLowerCase(java.util.Locale.ROOT);
                    boolean instructionTool = name.equals("load_skill") || name.endsWith(":load_skill")
                            || name.endsWith("__load_skill");
                    if (instructionTool && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                            && value.getAsString().startsWith("skill_instructions\n")) instructions.add(result);
                    maximumBytes = Math.max(maximumBytes, size);
                }
            }
        }
    }

    public boolean matches(ContextCheckpoint checkpoint, List<ModelMessage> source) {
        if (checkpoint.status() != ContextCheckpoint.Status.SUCCEEDED
                || checkpoint.sourceToIndexExclusive() > source.size()) return false;
        return checkpoint.sourceHash().equals(ContextSourceHash.compute(gson, source.subList(
                checkpoint.sourceFromIndex(), checkpoint.sourceToIndexExclusive())));
    }

    public Optional<ContextProjection> reuse(ContextCheckpoint checkpoint, String systemPrompt,
            List<ModelMessage> messages, int protectedFromIndex, List<ModelToolDefinition> tools) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        messages = List.copyOf(messages);
        if (checkpoint.status() != ContextCheckpoint.Status.SUCCEEDED
                || checkpoint.sourceFromIndex() != 0
                || checkpoint.sourceToIndexExclusive() > protectedFromIndex
                || !matches(checkpoint, messages.subList(0, protectedFromIndex))) return Optional.empty();
        JsonObject summary;
        try {
            List<ContextStructure.Unit> units = ContextStructure.units(messages);
            ContextStructure.requireBoundary(units, protectedFromIndex, messages.size());
            ContextStructure.requireBoundary(units, checkpoint.sourceToIndexExclusive(), messages.size());
            summary = parseSummary(checkpoint.summary());
        } catch (RuntimeException invalid) { return Optional.empty(); }
        List<ModelMessage> projected = summarized(summary,
                imageBlocks(messages.subList(0, checkpoint.sourceToIndexExclusive())),
                messages.subList(checkpoint.sourceToIndexExclusive(), messages.size()));
        Optional<ContextProjection> fitted = fitResults(systemPrompt, projected, tools);
        return fitted.map(value -> new ContextProjection(value.messages(),
                ContextProjection.Kind.SUMMARIZED, value.estimatedTokens()));
    }

    private Result failure(List<ModelMessage> source, int to, String code, String message, int estimate) {
        int requestedEnd = Math.min(Math.max(1, to), source.size());
        int end = ContextStructure.units(source).stream()
                .mapToInt(ContextStructure.Unit::toIndexExclusive)
                .filter(boundary -> boundary >= requestedEnd).findFirst().orElse(source.size());
        ContextCheckpoint checkpoint = new ContextCheckpoint(UUID.randomUUID(), 0, end,
                ContextSourceHash.compute(gson, source.subList(0, end)), modelIdentifier,
                clock.instant(), ContextCheckpoint.Status.FAILED, null, code, message,
                Math.max(0, estimate));
        return new Result(null, checkpoint, "context_compaction_failed", message);
    }

    private static JsonObject emptySummary() {
        JsonObject summary = new JsonObject();
        SUMMARY_FIELD_ORDER.forEach(field -> summary.add(field, new JsonArray()));
        return summary;
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
}
