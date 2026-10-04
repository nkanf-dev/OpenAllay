package dev.openallay.agent.context;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.model.*;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ContextProjectionAdmissionTest {
    @Test
    void nonmonotonicCountsNeverPublishAnUncheckedCandidate() {
        ContextTokenEstimator nativeLike = (prompt, messages, tools) -> {
            int bytes = new Utf8ContextTokenEstimator().estimate(prompt, messages, tools);
            // Token merges and omission metadata can make a shorter value cost more tokens.
            return bytes < 400 ? 2_000 : bytes < 800 ? 700 : bytes;
        };
        ContextBudget budget = new ContextBudget(1_100, 100);
        ContextCompactor compactor = compactor(nativeLike, budget);
        List<ModelMessage> source = exchange("field: observed value\n".repeat(2_000));
        JsonPrimitive witnessValue = (JsonPrimitive) dev.openallay.agent.tool.AgentToolResult.boundedModelValue(
                ((ModelContent.ToolResult) source.getLast().content().getFirst()).value(), 500);
        List<ModelMessage> witness = List.of(source.get(0), source.get(1),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("a", witnessValue, false))));
        assertEquals(700, nativeLike.estimate("system", witness, List.of()),
                "The unchanged budget has an actual structurally valid fitting candidate");
        var result = compactor.compact("system", source, 0, List.of(), false,
                "test", new CancellationSignal()).join();
        assertTrue(result.successful(), result::failureMessage);
        assertTrue(nativeLike.estimate("system", result.projection().messages(), List.of())
                <= budget.inputTokens());
        assertEquals(result.projection().estimatedTokens(),
                nativeLike.estimate("system", result.projection().messages(), List.of()));
        assertEquals("field: observed value\n".repeat(2_000),
                ((ModelContent.ToolResult) source.getLast().content().getFirst()).value().getAsString(),
                "The fitting archived view must not alter the original transcript");
    }

    @Test
    void manifestsAreDerivedFromSelectedCandidateAndRestoreAfterSearch() {
        ContextTokenEstimator estimator = new Utf8ContextTokenEstimator();
        ContextBudget budget = new ContextBudget(1_100, 100);
        ContextCompactor compactor = compactor(estimator, budget);
        List<ModelMessage> source = exchange("payload\n" + "x".repeat(40_000));
        AtomicInteger checks = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<List<ModelMessage>> last =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Function<List<ModelMessage>, String> manifest = candidate -> {
            checks.incrementAndGet();
            last.set(candidate);
            return "system\nmanifest: result bytes=" + candidate.getLast().content().stream()
                    .filter(ModelContent.ToolResult.class::isInstance)
                    .map(ModelContent.ToolResult.class::cast).findFirst().orElseThrow().value()
                    .toString().length();
        };
        var result = compactor.fitResults(manifest, source, List.of(), List.of()).orElseThrow();
        assertEquals(result.messages(), last.get());
        assertTrue(checks.get() > 1);
        assertTrue(estimator.estimate(manifest.apply(result.messages()), result.messages(), List.of())
                <= budget.inputTokens());
    }

    @Test
    void carriedMemoryMustFitBothContinuationAndNextSummaryChunk() {
        Utf8ContextTokenEstimator estimator = new Utf8ContextTokenEstimator();
        ContextBudget budget = new ContextBudget(2_400, 100);
        String empty = "{\"goals\":[],\"preferences\":[],\"completedTopics\":[],"
                + "\"currentTasks\":[],\"decisions\":[],\"unresolvedQuestions\":[],"
                + "\"evidenceReferences\":[]}";
        String oversizedCarry = empty.replace("\"goals\":[]", "\"goals\":[\"" + "m".repeat(500) + "\"]");
        String shortCarry = empty.replace("\"goals\":[]", "\"goals\":[\"goal retained\"]");
        java.util.ArrayList<ModelRequest> requests = new java.util.ArrayList<>();
        ModelClient model = (request, events, cancellation) -> {
            requests.add(request);
            assertTrue(estimator.estimate(request.systemPrompt(), request.messages(), request.tools())
                    <= budget.inputTokens());
            assertNotNull(request.maxOutputTokens());
            String response = requests.size() == 1 ? oversizedCarry : shortCarry;
            return CompletableFuture.completedFuture(new ModelTurn("test", "test",
                    List.of(new ModelContent.Text(response)), "end_turn", ModelUsage.empty()));
        };
        ContextCompactor compactor = new ContextCompactor(model, new Gson(), estimator, budget,
                "test", Clock.systemUTC());
        // Two complete historical units cannot enter one summary call. Their carry must coexist
        // with the second unit even when it would already fit the final current question.
        List<ModelMessage> messages = List.of(ModelMessage.userText("a".repeat(1_100)),
                ModelMessage.userText("b".repeat(1_100)), ModelMessage.userText("c".repeat(1_350)));
        var result = compactor.compact("system", messages, 2, List.of(), false,
                "test", new CancellationSignal()).join();
        assertTrue(result.successful(), result::failureMessage);
        assertEquals(3, requests.size(), "one bounded carry retry, then the second chunk");
        assertEquals(requests.get(0).messages(), requests.get(1).messages());
        assertTrue(estimator.estimate("system", result.projection().messages(), List.of())
                <= budget.inputTokens());
    }

    @Test
    void resultControlPlaneUsesConfiguredCompletionPlanRatherThanAllContextCapacity() {
        Utf8ContextTokenEstimator estimator = new Utf8ContextTokenEstimator();
        ContextBudget budget = new ContextBudget(100_000, 100);
        ContextCompactor compactor = compactor(estimator, budget);
        List<ModelMessage> source = exchange("payload\n" + "x".repeat(40_000));
        assertTrue(estimator.estimate("system", source, List.of()) < budget.inputTokens());
        assertTrue(compactor.requiresCompaction("system", source, List.of()),
                "Data control-plane planning is separate from the context admission ceiling");
        var result = compactor.compact("system", source, 0, List.of(), false,
                "test", new CancellationSignal()).join();
        assertTrue(result.successful(), result::failureMessage);
        ModelContent.ToolResult view = (ModelContent.ToolResult) result.projection()
                .messages().getLast().content().getFirst();
        assertTrue(view.value().getAsString().length() < 1_000,
                "Do not fill a large context window with the raw result");
        assertTrue(view.value().getAsString().contains("scope: archived preview"));
        assertTrue(view.value().getAsString().contains("no live workspace is implied"));
        assertEquals(40_008, ((ModelContent.ToolResult) source.getLast().content().getFirst())
                .value().getAsString().length(), "Original diagnostic text remains exact");
    }

    private static ContextCompactor compactor(ContextTokenEstimator estimator, ContextBudget budget) {
        return new ContextCompactor((request, events, cancellation) ->
                CompletableFuture.failedFuture(new AssertionError("No summary call expected")),
                new Gson(), estimator, budget, "test", Clock.systemUTC());
    }

    private static List<ModelMessage> exchange(String payload) {
        return List.of(ModelMessage.userText("answer the question"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse("a", "tool", new JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("a", new JsonPrimitive(payload), false))));
    }
}
