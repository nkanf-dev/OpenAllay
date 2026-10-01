package dev.openallay.agent.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelFailure;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ContextCompactorTest {
    private static final String SUMMARY = """
            {"goals":["build"],"preferences":[],"completedTopics":[],
             "currentTasks":["recipe"],"decisions":[],"unresolvedQuestions":[],
             "evidenceReferences":["viewer:jei/ref"]}
            """;

    @Test
    void returnsOriginalProjectionWithoutCallingModelWhenItFits() {
        FakeModel model = new FakeModel(textTurn(SUMMARY));
        ContextCompactor.Result result = compactor(model, new ContextBudget(10_000, 100))
                .compact("system", List.of(ModelMessage.userText("hello")), 0, List.of(),
                        true, "actor:main", new CancellationSignal())
                .join();

        assertTrue(result.successful());
        assertEquals(ContextProjection.Kind.ORIGINAL, result.projection().kind());
        assertNull(result.checkpoint());
        assertEquals(0, model.requests.size());
    }

    @Test
    void preservesActualPlaintextSuccessWhenOriginalContextFits() {
        assertOriginalPlaintextPreserved(
                "Inventory result\nFound 12 iron ingots.\nResult label: \"captured data\"", false);
    }

    @Test
    void preservesActualPlaintextErrorWhenOriginalContextFits() {
        assertOriginalPlaintextPreserved(
                "Error: JavaScript failed\ncode: javascript_root_unavailable\n"
                        + "Use roots [\"player\"] and access mc.player.",
                true);
    }

    @Test
    void summarizesExactHistoricalPlaintextErrorWithoutInventingMalformedFeedback() {
        String actualError = "Error: JavaScript failed\ncode: javascript_root_unavailable\n"
                + "Use roots [\"player\"] and access mc.player.\n"
                + "Current declared bare roots: player, game, items.\n"
                + "This error is actual tool feedback; no empty fact was returned.";
        List<ModelMessage> history = plaintextToolExchange("call-error", actualError, true);
        ModelMessage current = ModelMessage.userText("current:" + "x".repeat(650));
        List<ModelMessage> messages = List.of(history.get(0), history.get(1), current);
        FakeModel model = new FakeModel(textTurn(SUMMARY));
        ContextBudget budget = new ContextBudget(1_200, 100);
        ContextCompactor compactor = compactor(model, budget);
        assertTrue(compactor.requiresCompaction("system", messages, List.of()));

        ContextCompactor.Result result = compactor.compact(
                        "system", messages, 2, List.of(), true, "actor:errors",
                        new CancellationSignal())
                .join();

        assertTrue(result.successful());
        assertEquals(ContextProjection.Kind.SUMMARIZED, result.projection().kind());
        assertEquals(1, model.requests.size());
        ModelRequest summaryRequest = model.requests.getFirst();
        String summaryInput = ((ModelContent.Text) summaryRequest.messages()
                .getFirst().content().getFirst()).text();
        assertEquals(new Gson().toJson(history), summaryInput);
        JsonObject serializedError = JsonParser.parseString(summaryInput).getAsJsonArray()
                .get(1).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject();
        assertTrue(serializedError.get("value").isJsonPrimitive());
        assertEquals(actualError, serializedError.get("value").getAsString());
        assertTrue(serializedError.get("error").getAsBoolean());
        assertFalse(summaryInput.contains("context_result_malformed"));
        assertTrue(summaryRequest.systemPrompt().contains("Remember Skill workflow names"));
        assertTrue(summaryRequest.systemPrompt().replace('\n', ' ')
                .contains("never claim summarized Skill document text is still present"));
        assertEquals(current, result.projection().messages().getLast());
        assertTrue(result.projection().estimatedTokens() <= budget.inputTokens());
        assertEquals(2, result.checkpoint().sourceToIndexExclusive());
        assertTrue(compactor.matches(result.checkpoint(), messages));
        assertFalse(compactor.matches(result.checkpoint(), List.of(
                history.get(0),
                plaintextToolExchange("call-error", "rewritten error", true).get(1),
                current)));
    }

    @Test
    void usesSameSchedulingKeyAndCreatesSourceHashedDerivedSummary() {
        FakeModel model = new FakeModel(textTurn(SUMMARY));
        List<ModelMessage> messages = List.of(
                ModelMessage.userText("a".repeat(500)),
                ModelMessage.userText("b".repeat(500)),
                ModelMessage.userText("current"));
        ContextCompactor compactor = compactor(model, new ContextBudget(1_200, 100));

        ContextCompactor.Result result = compactor.compact(
                        "system", messages, 2, List.of(), true, "actor:recipes",
                        new CancellationSignal())
                .join();

        assertTrue(result.successful());
        assertEquals(ContextProjection.Kind.SUMMARIZED, result.projection().kind());
        assertEquals("actor:recipes", model.requests.getFirst().sessionKey());
        assertFalse(model.requests.getFirst().stream());
        assertTrue(result.projection().messages().getFirst().content().stream()
                .map(ModelContent.Text.class::cast)
                .anyMatch(text -> text.text().contains("NOT factual evidence")));
        ContextCheckpoint checkpoint = result.checkpoint();
        assertNotNull(checkpoint);
        assertEquals(ContextCheckpoint.Status.SUCCEEDED, checkpoint.status());
        assertEquals("test-model", checkpoint.modelIdentifier());
        assertTrue(compactor.matches(checkpoint, messages));

        List<ModelMessage> changed = new ArrayList<>(messages);
        changed.set(0, ModelMessage.userText("changed"));
        assertFalse(compactor.matches(checkpoint, changed));

        List<ModelMessage> nextRequest = new ArrayList<>(messages);
        nextRequest.add(ModelMessage.userText("next"));
        assertTrue(compactor.reuse(
                        checkpoint, "system", nextRequest, messages.size(), List.of())
                .isPresent());
        ContextCompactor otherModel = new ContextCompactor(
                model,
                new Gson(),
                new Utf8ContextTokenEstimator(),
                new ContextBudget(1_200, 100),
                "other-model",
                Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC));
        assertTrue(otherModel.reuse(
                        checkpoint, "system", nextRequest, messages.size(), List.of())
                .isPresent());
        nextRequest.set(0, ModelMessage.userText("stale"));
        assertTrue(compactor.reuse(
                        checkpoint, "system", nextRequest, messages.size(), List.of())
                .isEmpty());
    }

    @Test
    void reuseRejectsCorruptedCurrentSummaryOrSplitToolExchange() {
        FakeModel model = new FakeModel(textTurn(SUMMARY));
        ContextCompactor compactor = compactor(model, new ContextBudget(10_000, 100));
        List<ModelMessage> history = plaintextToolExchange("call-current", "actual error", true);
        List<ModelMessage> messages = List.of(
                history.get(0), history.get(1), ModelMessage.userText("current"));
        ContextCheckpoint corruptedSummary = new ContextCheckpoint(
                java.util.UUID.randomUUID(), 0, 2, ContextSourceHash.compute(new Gson(), history),
                "test-model", Instant.EPOCH, ContextCheckpoint.Status.SUCCEEDED,
                "{\"goals\":[]}", null, null, 100);
        assertTrue(compactor.matches(corruptedSummary, messages));
        assertTrue(compactor.reuse(
                        corruptedSummary, "system", messages, 2, List.of())
                .isEmpty());

        ContextCheckpoint splitExchange = new ContextCheckpoint(
                java.util.UUID.randomUUID(), 0, 1,
                ContextSourceHash.compute(new Gson(), history.subList(0, 1)),
                "test-model", Instant.EPOCH, ContextCheckpoint.Status.SUCCEEDED,
                SUMMARY, null, null, 100);
        assertTrue(compactor.matches(splitExchange, messages));
        assertTrue(compactor.reuse(splitExchange, "system", messages, 2, List.of()).isEmpty());
        assertTrue(model.requests.isEmpty());
    }

    @Test
    void malformedOrFailedSummaryReturnsStructuredTerminalFailure() {
        FakeModel malformed = new FakeModel(textTurn("not-json"));
        ContextCompactor.Result malformedResult = compactLarge(malformed);
        assertFalse(malformedResult.successful());
        assertEquals("context_compaction_failed", malformedResult.failureCode());
        assertEquals("summary_malformed", malformedResult.checkpoint().failureCode());

        FakeModel failed = new FakeModel(new ModelClientException(
                new ModelFailure("provider_down", "redacted", 503)));
        ContextCompactor.Result failedResult = compactLarge(failed);
        assertFalse(failedResult.successful());
        assertEquals("provider_down", failedResult.checkpoint().failureCode());
    }

    @Test
    void checkpointCodecIsStrictAndRoundTripsFailures() {
        ContextCheckpoint checkpoint = new ContextCheckpoint(
                java.util.UUID.randomUUID(), 0, 2, "a".repeat(64), "model",
                Instant.EPOCH, ContextCheckpoint.Status.FAILED, null,
                "summary_malformed", "bad", 900);
        ContextCheckpointCodec codec = new ContextCheckpointCodec();

        assertEquals(checkpoint, codec.decode(codec.encode(checkpoint)));
        JsonObject encoded = JsonParser.parseString(codec.encode(checkpoint)).getAsJsonObject();
        assertEquals(java.util.Set.of(
                "checkpointId", "sourceFromIndex", "sourceToIndexExclusive", "sourceHash",
                "modelIdentifier", "createdAt", "status", "summary", "failureCode",
                "failureMessage", "estimatedProjectionTokens"), encoded.keySet());
        JsonObject unknown = encoded.deepCopy();
        unknown.addProperty("unknown", true);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(unknown.toString()));
        JsonObject missing = encoded.deepCopy();
        missing.remove("sourceHash");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(missing.toString()));
        JsonObject corruptedHash = encoded.deepCopy();
        corruptedHash.addProperty("sourceHash", "not-a-source-hash");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(corruptedHash.toString()));
        JsonObject wrongType = encoded.deepCopy();
        wrongType.addProperty("sourceFromIndex", "0");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(wrongType.toString()));
    }

    @Test
    void cancellationBeforeCompactionSuppressesEveryModelCall() {
        FakeModel model = new FakeModel(textTurn(SUMMARY));
        CancellationSignal cancellation = new CancellationSignal();
        cancellation.cancel();

        ModelClientException failure = assertThrows(
                ModelClientException.class,
                () -> compactor(model, new ContextBudget(1_200, 100))
                        .compact("system", List.of(ModelMessage.userText("x")), 0,
                                List.of(), true, "actor:main", cancellation));

        assertEquals("agent_cancelled", failure.failure().code());
        assertEquals(0, model.requests.size());
    }

    @Test
    void cancellationSettlesNoncooperativeSummaryAndIgnoresLateSuccess() {
        CompletableFuture<ModelTurn> rawSummary = new CompletableFuture<>();
        List<ModelRequest> requests = new ArrayList<>();
        ModelClient model = (request, events, cancellation) -> {
            requests.add(request);
            return rawSummary;
        };
        ContextCompactor compactor = new ContextCompactor(
                model, new Gson(), new Utf8ContextTokenEstimator(), new ContextBudget(1_200, 100),
                "test-model", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        List<ModelMessage> messages = List.of(
                ModelMessage.userText("a".repeat(500)),
                ModelMessage.userText("b".repeat(500)),
                ModelMessage.userText("protected current ask"));
        CancellationSignal cancellation = new CancellationSignal();
        assertTrue(compactor.requiresCompaction("system", messages, List.of()));

        CompletableFuture<ContextCompactor.Result> running = compactor.compact(
                "system", messages, 2, List.of(), true, "actor:noncooperative-summary", cancellation);
        java.util.concurrent.atomic.AtomicInteger successfulResults =
                new java.util.concurrent.atomic.AtomicInteger();
        running.thenAccept(result -> {
            if (result.successful()) successfulResults.incrementAndGet();
        });
        assertEquals(1, requests.size());
        assertFalse(requests.getFirst().stream());
        assertFalse(running.isDone());
        assertTrue(cancellation.cancel());

        assertTrue(running.isCompletedExceptionally());
        assertFalse(rawSummary.isDone(), "the raw summary provider does not cooperate with cancel");
        java.util.concurrent.CompletionException failure = assertThrows(
                java.util.concurrent.CompletionException.class, running::join);
        assertTrue(failure.getCause() instanceof ModelClientException);
        assertEquals("agent_cancelled", ((ModelClientException) failure.getCause()).failure().code());
        assertEquals(0, successfulResults.get());

        assertTrue(rawSummary.complete(textTurn(SUMMARY)));
        java.util.concurrent.CompletionException lateFailure = assertThrows(
                java.util.concurrent.CompletionException.class, running::join);
        assertEquals("agent_cancelled", ((ModelClientException) lateFailure.getCause()).failure().code());
        assertEquals(0, successfulResults.get(), "late summary must not publish a successful checkpoint");
        assertEquals(1, requests.size(), "canceled summary must not trigger any primary model call");
    }

    private static void assertOriginalPlaintextPreserved(String actualText, boolean error) {
        List<ModelMessage> messages = plaintextToolExchange("call-plaintext", actualText, error);
        FakeModel model = new FakeModel(textTurn(SUMMARY));

        ContextCompactor.Result result = compactor(model, new ContextBudget(10_000, 100))
                .compact("system", messages, messages.size(), List.of(), true, "actor:plaintext",
                        new CancellationSignal())
                .join();

        assertTrue(result.successful());
        assertEquals(ContextProjection.Kind.ORIGINAL, result.projection().kind());
        assertEquals(messages, result.projection().messages());
        ModelContent.ToolResult toolResult = (ModelContent.ToolResult) result.projection()
                .messages().getLast().content().getFirst();
        assertTrue(toolResult.value().isJsonPrimitive());
        assertEquals(actualText, toolResult.value().getAsString());
        assertEquals(error, toolResult.error());
        assertFalse(toolResult.value().getAsString().contains("context_result_malformed"));
        assertNull(result.checkpoint());
        assertTrue(model.requests.isEmpty());
    }

    private static List<ModelMessage> plaintextToolExchange(
            String id, String actualText, boolean error) {
        return List.of(
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        id, "openallay__run_javascript", new JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        id, new JsonPrimitive(actualText), error))));
    }

    private static ContextCompactor.Result compactLarge(FakeModel model) {
        return compactor(model, new ContextBudget(1_200, 100))
                .compact("system", List.of(
                                ModelMessage.userText("a".repeat(500)),
                                ModelMessage.userText("b".repeat(500)),
                                ModelMessage.userText("current")),
                        2, List.of(), true, "actor:main", new CancellationSignal())
                .join();
    }

    private static ContextCompactor compactor(FakeModel model, ContextBudget budget) {
        return new ContextCompactor(
                model,
                new Gson(),
                new Utf8ContextTokenEstimator(),
                budget,
                "test-model",
                Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC));
    }

    private static ModelTurn textTurn(String text) {
        return new ModelTurn("test", "test-model", List.of(new ModelContent.Text(text)),
                "end_turn", ModelUsage.empty());
    }

    private static final class FakeModel implements ModelClient {
        private final Object outcome;
        private final List<ModelRequest> requests = new ArrayList<>();

        private FakeModel(Object outcome) {
            this.outcome = outcome;
        }

        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request,
                Consumer<ModelEvent> events,
                CancellationSignal cancellation) {
            requests.add(request);
            if (outcome instanceof RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
            return CompletableFuture.completedFuture((ModelTurn) outcome);
        }
    }
}
