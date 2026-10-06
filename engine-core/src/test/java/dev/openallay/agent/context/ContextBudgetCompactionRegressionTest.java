package dev.openallay.agent.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ContextBudgetCompactionRegressionTest {
    private static final Gson GSON = dev.openallay.json.EngineJson.create();
    private static final ContextTokenEstimator ESTIMATOR = new Utf8ContextTokenEstimator();
    private static final String SYSTEM = "system";
    private static final List<String> SUMMARY_FIELDS = List.of(
            "goals", "preferences", "completedTopics", "currentTasks", "decisions",
            "unresolvedQuestions", "evidenceReferences");
    private static final String SUMMARY = """
            {"goals":["inspect result"],"preferences":[],"completedTopics":[],
             "currentTasks":["answer current question"],"decisions":[],
             "unresolvedQuestions":[],"evidenceReferences":[]}
            """;

    @Test
    void projectsMegabyteCurrentSuccessWithoutSummarizingProtectedTurnOrMutatingSource() {
        ContextBudget budget = new ContextBudget(5_000, 100);
        BudgetCheckingModel model = new BudgetCheckingModel(budget, SUMMARY);
        ModelMessage question = ModelMessage.userText("Inspect the completed result and report its status.");
        JsonObject originalValue = hugeSuccessfulValue();
        List<ModelMessage> exchange = toolExchange("current-success", originalValue, false);
        List<ModelMessage> messages = List.of(question, exchange.get(0), exchange.get(1));
        String sourceHash = ContextSourceHash.compute(GSON, messages);
        assertTrue(ESTIMATOR.estimate(SYSTEM, messages, List.of()) > budget.inputTokens());

        ContextCompactor.Result result = compact(model, budget, messages, 0);

        assertFits(result, budget);
        assertEquals(question, result.projection().messages().getFirst());
        ModelContent.ToolResult projected = assertExchangeRetained(
                result.projection().messages(), exchange, "current-success");
        assertFalse(projected.error());
        assertNotEquals(originalValue, projected.value(), "large success needs a bounded projection");
        assertTrue(model.requests.isEmpty(), "completed current successes do not need a summary model");
        assertEquals(sourceHash, ContextSourceHash.compute(GSON, messages));
        assertEquals(originalValue, resultFor(messages, "current-success").value());
        assertEquals(1_600_000, resultFor(messages, "current-success")
                .value().getAsJsonObject().get("payload").getAsString().length());
    }

    @Test
    void currentSuccessProjectionKeepsExactQuestionAndActualErrorFeedback() {
        ContextBudget budget = new ContextBudget(5_000, 100);
        BudgetCheckingModel model = new BudgetCheckingModel(budget, SUMMARY);
        ModelMessage question = ModelMessage.userText("Explain the successful inspection and this actual error.");
        List<ModelMessage> success = toolExchange("success-with-error", hugeSuccessfulValue(), false);
        String actualError = "Error: operation failed\ncode: invalid_operation\n"
                + "The requested operation did not run; use the available operation instead.";
        List<ModelMessage> error = toolExchange("actual-error", new JsonPrimitive(actualError), true);
        List<ModelMessage> messages = List.of(
                question, success.get(0), success.get(1), error.get(0), error.get(1));
        String sourceHash = ContextSourceHash.compute(GSON, messages);

        ContextCompactor.Result result = compact(model, budget, messages, 0);

        assertFits(result, budget);
        assertEquals(question, result.projection().messages().getFirst());
        assertFalse(assertExchangeRetained(
                result.projection().messages(), success, "success-with-error").error());
        ModelContent.ToolResult retainedError = assertExchangeRetained(
                result.projection().messages(), error, "actual-error");
        assertEquals(resultFor(error, "actual-error"), retainedError);
        assertEquals(actualError, retainedError.value().getAsString());
        assertTrue(retainedError.error());
        assertTrue(model.requests.isEmpty());
        assertEquals(sourceHash, ContextSourceHash.compute(GSON, messages));
    }

    @Test
    void hugeHistoricalSuccessAndCurrentExchangeFitWithoutAnyUnsafeSummaryRequest() {
        ContextBudget budget = new ContextBudget(5_000, 100);
        BudgetCheckingModel model = new BudgetCheckingModel(budget, SUMMARY);
        List<ModelMessage> history = toolExchange("historical-success", hugeSuccessfulValue(), false);
        ModelMessage question = ModelMessage.userText("Compare the earlier result with the current result.");
        List<ModelMessage> current = toolExchange(
                "new-success", new JsonPrimitive("current result: " + "n".repeat(40_000)), false);
        List<ModelMessage> messages = List.of(
                history.get(0), history.get(1), question, current.get(0), current.get(1));
        String sourceHash = ContextSourceHash.compute(GSON, messages);

        ContextCompactor.Result result = compact(model, budget, messages, 2);

        assertFits(result, budget);
        assertTrue(result.projection().messages().contains(question));
        assertFalse(assertExchangeRetained(
                result.projection().messages(), current, "new-success").error());
        if (resultFor(result.projection().messages(), "historical-success") != null) {
            assertFalse(assertExchangeRetained(
                    result.projection().messages(), history, "historical-success").error());
        } else {
            assertEquals(ContextProjection.Kind.SUMMARIZED, result.projection().kind());
            assertNotNull(result.checkpoint());
            assertEquals(ContextCheckpoint.Status.SUCCEEDED, result.checkpoint().status());
        }
        assertEquals(sourceHash, ContextSourceHash.compute(GSON, messages));
        assertEquals(1_600_000, resultFor(messages, "historical-success")
                .value().getAsJsonObject().get("payload").getAsString().length());
        model.assertAllRequestsFit();
    }

    @Test
    void retriesValidOverlongSummaryOnceToFitTheRemainingFinalInputBudget() {
        ContextBudget budget = new ContextBudget(1_200, 100);
        String summaryResponse = overlongSummary();
        BudgetCheckingModel model = new BudgetCheckingModel(budget, summaryResponse, SUMMARY);
        ModelMessage current = ModelMessage.userText("current question must remain exact");
        List<ModelMessage> messages = List.of(
                ModelMessage.userText("a".repeat(500)),
                ModelMessage.userText("b".repeat(500)),
                current);
        String sourceHash = ContextSourceHash.compute(GSON, messages);
        assertTrue(ESTIMATOR.estimate(SYSTEM, messages, List.of()) > budget.inputTokens());

        ContextCompactor.Result result = compact(model, budget, messages, 2);

        assertFits(result, budget);
        assertEquals(ContextProjection.Kind.SUMMARIZED, result.projection().kind());
        assertEquals(current, result.projection().messages().getLast());
        assertEquals(2, model.requests.size(), "overlong valid summary allows exactly one targeted retry");
        assertEquals(model.requests.getFirst().messages(), model.requests.getLast().messages(),
                "retry must use the already admitted source, not the overlong response");
        ContextCheckpoint checkpoint = result.checkpoint();
        assertNotNull(checkpoint);
        assertEquals(ContextCheckpoint.Status.SUCCEEDED, checkpoint.status());
        JsonObject summary = dev.openallay.json.JsonTrees.parse(checkpoint.summary()).getAsJsonObject();
        assertEquals(Set.copyOf(SUMMARY_FIELDS), dev.openallay.json.JsonTrees.keys(summary));
        for (String field : SUMMARY_FIELDS) {
            assertTrue(summary.get(field).isJsonArray(), "summary field: " + field);
            for (JsonElement value : summary.getAsJsonArray(field)) {
                assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString());
            }
        }
        assertEquals(dev.openallay.json.JsonTrees.parse(SUMMARY), summary,
                "retry response must remain intact rather than lose selected facts locally");
        assertTrue(result.projection().messages().getFirst().content().stream()
                .filter(ModelContent.Text.class::isInstance)
                .map(ModelContent.Text.class::cast)
                .anyMatch(text -> text.text().contains("NOT factual evidence")));
        assertEquals(sourceHash, ContextSourceHash.compute(GSON, messages));
        model.assertAllRequestsFit();
    }

    @Test
    void persistentlyOverlongValidSummaryFailsAfterOneSafeRetry() {
        ContextBudget budget = new ContextBudget(1_200, 100);
        String summaryResponse = overlongSummary();
        BudgetCheckingModel model = new BudgetCheckingModel(budget, summaryResponse, summaryResponse);
        ModelMessage current = ModelMessage.userText("current question must remain exact");
        List<ModelMessage> messages = List.of(
                ModelMessage.userText("a".repeat(500)),
                ModelMessage.userText("b".repeat(500)),
                current);
        String sourceHash = ContextSourceHash.compute(GSON, messages);

        ContextCompactor.Result result = compact(model, budget, messages, 2);

        assertFalse(result.successful());
        assertNull(result.projection());
        assertEquals("context_compaction_failed", result.failureCode());
        assertNotNull(result.checkpoint());
        assertEquals(ContextCheckpoint.Status.FAILED, result.checkpoint().status());
        assertEquals("summary_output_over_budget", result.checkpoint().failureCode());
        assertNull(result.checkpoint().summary());
        assertEquals(2, model.requests.size(), "valid overlong output must not cause an unbounded retry");
        assertEquals(model.requests.getFirst().messages(), model.requests.getLast().messages(),
                "retry must use the already admitted source, not the overlong response");
        assertEquals(sourceHash, ContextSourceHash.compute(GSON, messages));
        model.assertAllRequestsFit();
    }

    @Test
    void intrinsicallyUnfitProtectedQuestionAndActualErrorFailBeforeEveryModelCall() {
        ContextBudget budget = new ContextBudget(5_000, 100);
        BudgetCheckingModel model = new BudgetCheckingModel(budget, SUMMARY);
        ModelMessage history = ModelMessage.userText("historical context that could be summarized");
        ModelMessage question = ModelMessage.userText("Exact protected request: " + "q".repeat(2_400));
        String actualError = "Actual error: operation failed\n" + "detail".repeat(600);
        List<ModelMessage> error = toolExchange("unfit-error", new JsonPrimitive(actualError), true);
        List<ModelMessage> messages = List.of(history, question, error.get(0), error.get(1));
        String sourceHash = ContextSourceHash.compute(GSON, messages);
        assertTrue(ESTIMATOR.estimate(SYSTEM, List.of(question), List.of()) <= budget.inputTokens());
        assertTrue(ESTIMATOR.estimate(SYSTEM, error, List.of()) <= budget.inputTokens());
        assertTrue(ESTIMATOR.estimate(SYSTEM, messages.subList(1, messages.size()), List.of())
                > budget.inputTokens());

        ContextCompactor.Result result = compact(model, budget, messages, 1);

        assertFalse(result.successful());
        assertNull(result.projection());
        assertEquals("context_compaction_failed", result.failureCode());
        assertNotNull(result.checkpoint());
        assertEquals(ContextCheckpoint.Status.FAILED, result.checkpoint().status());
        assertEquals("fixed_context_over_budget", result.checkpoint().failureCode());
        assertNotNull(result.checkpoint().failureMessage());
        assertFalse(result.checkpoint().failureMessage().isBlank());
        assertNull(result.checkpoint().summary());
        assertTrue(model.requests.isEmpty(), "unfit fixed context cannot justify a summary request");
        assertEquals(sourceHash, ContextSourceHash.compute(GSON, messages));
        assertEquals(actualError, resultFor(messages, "unfit-error").value().getAsString());
        assertTrue(resultFor(messages, "unfit-error").error());
    }

    @Test
    void smallBudgetsCanProjectCurrentSuccessWhenMandatoryFramingFits() {
        for (ContextBudget budget : List.of(new ContextBudget(768, 16), new ContextBudget(1_024, 32))) {
            BudgetCheckingModel model = new BudgetCheckingModel(budget, SUMMARY);
            ModelMessage question = ModelMessage.userText("q");
            List<ModelMessage> exchange = toolExchange("tiny", new JsonPrimitive("v".repeat(8_000)), false);
            List<ModelMessage> messages = List.of(question, exchange.get(0), exchange.get(1));
            List<ModelMessage> minimalExchange = toolExchange("tiny", new JsonPrimitive(""), false);
            List<ModelMessage> framing = List.of(
                    question, minimalExchange.get(0), minimalExchange.get(1));
            String sourceHash = ContextSourceHash.compute(GSON, messages);
            assertTrue(ESTIMATOR.estimate(SYSTEM, framing, List.of()) <= budget.inputTokens());
            assertTrue(ESTIMATOR.estimate(SYSTEM, messages, List.of()) > budget.inputTokens());

            ContextCompactor.Result result = compact(model, budget, messages, 0);

            assertFits(result, budget);
            assertEquals(question, result.projection().messages().getFirst());
            assertFalse(assertExchangeRetained(result.projection().messages(), exchange, "tiny").error());
            assertTrue(model.requests.isEmpty());
            assertEquals(sourceHash, ContextSourceHash.compute(GSON, messages));
        }
    }

    private static String overlongSummary() {
        JsonObject summary = new JsonObject();
        for (String field : SUMMARY_FIELDS) {
            JsonArray values = new JsonArray();
            values.add(field + ":" + "s".repeat(3_000));
            summary.add(field, values);
        }
        return summary.toString();
    }

    private static JsonObject hugeSuccessfulValue() {
        JsonObject value = new JsonObject();
        value.addProperty("status", "completed");
        value.addProperty("count", 42);
        value.addProperty("payload", "p".repeat(1_600_000));
        return value;
    }

    private static List<ModelMessage> toolExchange(String id, JsonElement value, boolean error) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("operation", "inspect");
        return List.of(
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(id, "tool", arguments))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(id, value, error))));
    }

    private static ModelContent.ToolResult assertExchangeRetained(
            List<ModelMessage> projected, List<ModelMessage> sourceExchange, String id) {
        List<ContextStructure.Unit> units = ContextStructure.units(projected);
        ContextStructure.Unit retained = units.stream()
                .filter(ContextStructure.Unit::toolExchange)
                .filter(unit -> unit.messages().getFirst().content().stream()
                        .filter(ModelContent.ToolUse.class::isInstance)
                        .map(ModelContent.ToolUse.class::cast)
                        .anyMatch(use -> use.id().equals(id)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing complete tool exchange " + id));
        assertEquals(sourceExchange.getFirst(), retained.messages().getFirst(),
                "tool-use id, name and arguments must remain exact");
        assertEquals(ModelRole.USER, retained.messages().getLast().role());
        ModelContent.ToolResult result = resultFor(retained.messages(), id);
        assertNotNull(result);
        assertEquals(id, result.toolUseId());
        assertEquals(resultFor(sourceExchange, id).error(), result.error());
        return result;
    }

    private static ModelContent.ToolResult resultFor(List<ModelMessage> messages, String id) {
        return messages.stream()
                .flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolResult.class::isInstance)
                .map(ModelContent.ToolResult.class::cast)
                .filter(result -> result.toolUseId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private static ContextCompactor.Result compact(
            BudgetCheckingModel model, ContextBudget budget, List<ModelMessage> messages, int protectedFromIndex) {
        ContextCompactor compactor = new ContextCompactor(
                model, GSON, ESTIMATOR, budget, "test-model", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        return compactor.compact(
                        SYSTEM, messages, protectedFromIndex, List.of(), true,
                        "actor:context-budget-regression", new CancellationSignal())
                .join();
    }

    private static void assertFits(ContextCompactor.Result result, ContextBudget budget) {
        assertTrue(result.successful(), () -> result.failureCode() + ": " + result.failureMessage());
        int actualEstimate = ESTIMATOR.estimate(SYSTEM, result.projection().messages(), List.of());
        assertEquals(actualEstimate, result.projection().estimatedTokens());
        assertTrue(actualEstimate <= budget.inputTokens(),
                () -> "projection estimate " + actualEstimate + " exceeds input budget " + budget.inputTokens());
        ContextStructure.units(result.projection().messages());
    }

    private static final class BudgetCheckingModel implements ModelClient {
        private final ContextBudget budget;
        private final List<String> summaries;
        private final List<ModelRequest> requests = new ArrayList<>();

        private BudgetCheckingModel(ContextBudget budget, String... summaries) {
            this.budget = budget;
            this.summaries = List.of(summaries);
        }

        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request, Consumer<ModelEvent> events, CancellationSignal cancellation) {
            requests.add(request);
            assertRequestFits(request);
            assertTrue(requests.size() <= summaries.size(), "unexpected extra summary model request");
            String summary = summaries.get(requests.size() - 1);
            return CompletableFuture.completedFuture(new ModelTurn(
                    "test", "test-model", List.of(new ModelContent.Text(summary)),
                    "end_turn", ModelUsage.empty()));
        }

        private void assertAllRequestsFit() {
            requests.forEach(this::assertRequestFits);
        }

        private void assertRequestFits(ModelRequest request) {
            int estimate = ESTIMATOR.estimate(request.systemPrompt(), request.messages(), request.tools());
            assertTrue(estimate <= budget.inputTokens(),
                    () -> "unsafe model input " + estimate + " exceeds budget " + budget.inputTokens());
            assertNotNull(request.maxOutputTokens(), "compaction requests require an output-token cap");
            assertTrue(request.maxOutputTokens() > 0);
            assertTrue(request.maxOutputTokens() <= budget.maxOutputTokens());
        }
    }
}
