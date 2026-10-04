package dev.openallay.agent.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.model.image.ImageReference;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class ContextCompactorManualTest {
    private static final Gson GSON = new Gson();
    private static final String DERIVED_PREFIX =
            "[OpenAllay derived conversation memory; NOT factual evidence]\n";
    private static final String SUMMARY = """
            {"goals":["build"],"preferences":[],"completedTopics":[],"currentTasks":[],
             "decisions":[],"unresolvedQuestions":[],"evidenceReferences":[]}
            """;
    private static final String EMPTY_SUMMARY = """
            {"goals":[],"preferences":[],"completedTopics":[],"currentTasks":[],
             "decisions":[],"unresolvedQuestions":[],"evidenceReferences":[]}
            """;
    private static final ContextTokenEstimator ESTIMATOR = new Utf8ContextTokenEstimator();

    @Test
    void forcedUnderBudgetCompactionReallyCallsSummaryModelAndRecomputesPrompt() {
        ContextBudget budget = new ContextBudget(10_000, 512);
        RecordingModel model = new RecordingModel(budget, SUMMARY);
        ContextCompactor compactor = compactor(model, budget);
        List<ModelMessage> messages = history("a".repeat(600), "b".repeat(600));
        AtomicReference<List<ModelMessage>> lastPrompt = new AtomicReference<>();
        Function<List<ModelMessage>, String> prompt = candidate -> {
            lastPrompt.set(candidate);
            return "system; exact projection messages=" + candidate.size();
        };
        assertFalse(compactor.requiresCompaction(prompt.apply(messages), messages, List.of()));

        ContextCompactor.Result result = compactor.compactManually(
                prompt, messages, 2, List.of(), "actor:manual", new CancellationSignal()).join();

        assertTrue(result.successful(), result::failureMessage);
        assertEquals(ContextProjection.Kind.SUMMARIZED, result.projection().kind());
        assertEquals(1, model.requests.size(), "fitting input must not skip a forced summary");
        assertEquals("actor:manual", model.requests.getFirst().sessionKey());
        assertFalse(model.requests.getFirst().stream());
        assertEquals(messages.getLast(), result.projection().messages().getLast());
        assertEquals(result.projection().messages(), lastPrompt.get());
        assertEquals(ESTIMATOR.estimate(prompt.apply(result.projection().messages()),
                result.projection().messages(), List.of()), result.projection().estimatedTokens());
        assertTrue(result.projection().estimatedTokens() < ESTIMATOR.estimate(
                prompt.apply(messages), messages, List.of()));
        assertEquals(2, result.checkpoint().sourceToIndexExclusive());
        assertTrue(compactor.matches(result.checkpoint(), messages));
        assertEquals(JsonParser.parseString(SUMMARY), JsonParser.parseString(result.checkpoint().summary()));
    }

    @Test
    void smallHistoryOrNoCompleteOlderCandidateIsAnExplicitOriginalNoOp() {
        ContextBudget budget = new ContextBudget(10_000, 512);
        for (List<ModelMessage> messages : List.of(
                List.of(ModelMessage.userText("only current")),
                history("old", "done"))) {
            RecordingModel model = new RecordingModel(budget, SUMMARY);
            ContextCompactor.Result result = compactor(model, budget).compactManually(
                    ignored -> "system", messages, messages.size() - 1, List.of(),
                    "actor:small", new CancellationSignal()).join();
            assertOriginal(result, messages);
            assertTrue(model.requests.isEmpty());
        }
        RecordingModel protectedModel = new RecordingModel(budget, SUMMARY);
        List<ModelMessage> messages = history("a".repeat(600), "b".repeat(600));
        ContextCompactor.Result protectedResult = compactor(protectedModel, budget).compactManually(
                ignored -> "system", messages, 0, List.of(), "actor:protected", new CancellationSignal()).join();
        assertOriginal(protectedResult, messages);
        assertTrue(protectedModel.requests.isEmpty());
    }

    @Test
    void originalNoOpAlsoRemovesProviderPrivateReasoningWithoutChangingActualText() {
        ContextBudget budget = new ContextBudget(10_000, 512);
        RecordingModel model = new RecordingModel(budget, SUMMARY);
        List<ModelMessage> source = List.of(ModelMessage.userText("current"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.Reasoning("private no-op", "signature"),
                        new ModelContent.Text("actual answer"))));
        ContextCompactor.Result result = compact(model, budget, source, 0);
        assertOriginal(result, source);
        assertEquals(List.of(new ModelContent.Text("actual answer")), result.projection().messages().getLast().content());
        assertTrue(model.requests.isEmpty());
        assertEquals(ESTIMATOR.estimate("system", result.projection().messages(), List.of()),
                result.projection().estimatedTokens());
        assertTrue(source.getLast().content().getFirst() instanceof ModelContent.Reasoning);
    }

    @Test
    void priorDerivedSummaryAloneIsNotSubstantiveOlderHistory() {
        ContextBudget budget = new ContextBudget(10_000, 512);
        RecordingModel model = new RecordingModel(budget, SUMMARY);
        List<ModelMessage> messages = List.of(
                ModelMessage.userText(DERIVED_PREFIX + summaryWithGoal("m".repeat(2_000))),
                ModelMessage.userText("current"));
        ContextCompactor.Result result = compactor(model, budget).compactManually(
                ignored -> "system", messages, 1, List.of(), "actor:prior-only", new CancellationSignal()).join();
        assertOriginal(result, messages);
        assertTrue(model.requests.isEmpty(), "do not repeatedly summarize only derived memory");
    }

    @Test
    void aRealSummaryWithoutTokenBenefitReturnsOriginalAndNoCheckpoint() {
        ContextBudget budget = new ContextBudget(10_000, 512);
        RecordingModel model = new RecordingModel(budget, summaryWithGoal("s".repeat(280)));
        List<ModelMessage> messages = history("a".repeat(160), "b".repeat(160));
        ContextCompactor.Result result = compactor(model, budget).compactManually(
                ignored -> "system", messages, 2, List.of(), "actor:no-benefit", new CancellationSignal()).join();
        assertEquals(1, model.requests.size(), "the smallest legal summary had a possible reduction");
        assertOriginal(result, messages);
    }

    @Test
    void retainsLatestCompletedTurnAndExactSkillAndToolRangesWithoutPrivateReasoning() {
        ContextBudget budget = new ContextBudget(12_000, 512);
        RecordingModel model = new RecordingModel(budget, SUMMARY);
        List<ModelMessage> oldExchange = exchange("old", "tool", "old success", false);
        String skillText = "skill_instructions\nworkflow: builder\n" + "instructions\n".repeat(100);
        List<ModelMessage> skill = exchange("skill", "openallay__load_skill", skillText, false);
        String exactLargeResult = "recent success " + "r".repeat(1_500);
        List<ModelMessage> recent = exchange("recent", "tool", exactLargeResult, false);
        ModelMessage current = ModelMessage.userText("current user turn stays complete");
        ModelMessage assistant = new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.Reasoning("recent private", "secret-signature"),
                new ModelContent.Text("visible latest answer")));
        List<ModelMessage> source = List.of(
                ModelMessage.userText("old question " + "q".repeat(600)),
                oldExchange.getFirst(), oldExchange.getLast(),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.Reasoning("historical private", "signature"),
                        new ModelContent.Text("old answer " + "a".repeat(600)))),
                current, skill.getFirst(), skill.getLast(), recent.getFirst(), recent.getLast(), assistant);
        String sourceHash = ContextSourceHash.compute(GSON, source);
        List<ModelMessage> expectedSuffix = ModelContextCodec.safe(source.subList(4, source.size()));
        Function<List<ModelMessage>, String> skillPrompt = projection -> "system; skill present="
                + projection.stream().flatMap(message -> message.content().stream())
                        .filter(ModelContent.ToolResult.class::isInstance)
                        .map(ModelContent.ToolResult.class::cast)
                        .anyMatch(result -> result.value().equals(new JsonPrimitive(skillText)));

        ContextCompactor.Result result = compactor(model, budget).compactManually(
                skillPrompt, source, source.size(), List.of(), "actor:atomic", new CancellationSignal()).join();

        assertTrue(result.successful(), result::failureMessage);
        assertEquals(ContextProjection.Kind.SUMMARIZED, result.projection().kind());
        assertEquals(4, result.checkpoint().sourceToIndexExclusive(), "latest completed user turn is protected");
        assertEquals(expectedSuffix, result.projection().messages().subList(1, result.projection().messages().size()));
        assertEquals(2, ContextStructure.units(result.projection().messages()).stream()
                .filter(ContextStructure.Unit::toolExchange).count());
        assertEquals(skillText, resultValue(result.projection().messages(), "skill"));
        assertEquals(exactLargeResult, resultValue(result.projection().messages(), "recent"),
                "manual compaction does not clip protected results even when fitResults would");
        assertFalse(result.projection().messages().stream().flatMap(message -> message.content().stream())
                .anyMatch(ModelContent.Reasoning.class::isInstance));
        for (ModelRequest request : model.requests) {
            String input = GSON.toJson(request.messages());
            assertFalse(input.contains("historical private"));
            assertFalse(input.contains("recent private"));
            assertFalse(input.contains("secret-signature"));
        }
        assertEquals(sourceHash, ContextSourceHash.compute(GSON, source));
        assertTrue(source.getLast().content().getFirst() instanceof ModelContent.Reasoning);
    }

    @Test
    void reasoningOnlyHistoryKeepsOriginalSourceBoundariesAndRetiresOlderSkillPresence() {
        ContextBudget budget = new ContextBudget(10_000, 512);
        RecordingModel model = new RecordingModel(budget, SUMMARY);
        String skillText = "skill_instructions\nworkflow: builder\n" + "instructions\n".repeat(100);
        List<ModelMessage> skill = exchange("old-skill", "openallay__load_skill", skillText, false);
        List<ModelMessage> source = List.of(
                ModelMessage.userText("old question " + "q".repeat(600)),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Reasoning("private-only", "signature"))),
                skill.getFirst(), skill.getLast(), ModelMessage.userText("current"));
        AtomicReference<List<ModelMessage>> last = new AtomicReference<>();
        Function<List<ModelMessage>, String> prompt = projection -> {
            last.set(projection);
            return "system; loaded Skill=" + projection.stream().flatMap(message -> message.content().stream())
                    .filter(ModelContent.ToolResult.class::isInstance).map(ModelContent.ToolResult.class::cast)
                    .anyMatch(result -> result.value().equals(new JsonPrimitive(skillText)));
        };
        ContextCompactor compactor = compactor(model, budget);
        ContextCompactor.Result result = compactor.compactManually(prompt, source, 4, List.of(),
                "actor:skill-retired", new CancellationSignal()).join();

        assertTrue(result.successful(), result::failureMessage);
        assertEquals(ContextProjection.Kind.SUMMARIZED, result.projection().kind());
        assertEquals(4, result.checkpoint().sourceToIndexExclusive(), "checkpoint uses original, not safe-view indexes");
        assertTrue(compactor.matches(result.checkpoint(), source));
        assertEquals(result.projection().messages(), last.get());
        assertEquals("system; loaded Skill=false", prompt.apply(result.projection().messages()));
        assertEquals(source.getLast(), result.projection().messages().getLast());
        assertFalse(GSON.toJson(model.requests).contains("private-only"));
    }

    @Test
    void suppliedProtectedBoundaryCannotSplitAtomicToolExchange() {
        ContextBudget budget = new ContextBudget(10_000, 512);
        RecordingModel model = new RecordingModel(budget, SUMMARY);
        List<ModelMessage> exchange = exchange("call", "tool", "actual result", false);
        List<ModelMessage> source = List.of(exchange.getFirst(), exchange.getLast(), ModelMessage.userText("current"));
        assertThrows(IllegalArgumentException.class, () -> compactor(model, budget).compactManually(
                ignored -> "system", source, 1, List.of(), "actor:split", new CancellationSignal()));
        assertTrue(model.requests.isEmpty());
    }

    @Test
    void malformedAndProviderFailuresDoNotPublishAnOriginalOrSuccessfulCheckpoint() {
        ContextBudget budget = new ContextBudget(10_000, 512);
        RecordingModel malformed = new RecordingModel(budget, "not-json");
        ContextCompactor.Result invalid = compact(malformed, budget, history("a".repeat(600), "b".repeat(600)), 2);
        assertFalse(invalid.successful());
        assertNull(invalid.projection());
        assertEquals("summary_malformed", invalid.checkpoint().failureCode());
        assertEquals(ContextCheckpoint.Status.FAILED, invalid.checkpoint().status());
        assertEquals(1, malformed.requests.size());

        ModelClient failed = (request, events, cancellation) -> CompletableFuture.failedFuture(
                new ModelClientException(new ModelFailure("provider_down", "unavailable", 503)));
        ContextCompactor.Result failure = compactor(failed, budget).compactManually(
                ignored -> "system", history("a".repeat(600), "b".repeat(600)), 2, List.of(),
                "actor:failure", new CancellationSignal()).join();
        assertFalse(failure.successful());
        assertEquals("provider_down", failure.checkpoint().failureCode());
    }

    @Test
    void cancellationPreventsAdmissionAndSettlesNoncooperativeSummaryWithoutLateSuccess() {
        ContextBudget budget = new ContextBudget(10_000, 512);
        RecordingModel model = new RecordingModel(budget, SUMMARY);
        CancellationSignal before = new CancellationSignal();
        before.cancel();
        ModelClientException early = assertThrows(ModelClientException.class, () -> compactor(model, budget)
                .compactManually(ignored -> "system", history("a".repeat(600), "b".repeat(600)),
                        2, List.of(), "actor:cancel", before));
        assertEquals("agent_cancelled", early.failure().code());
        assertTrue(model.requests.isEmpty());

        CompletableFuture<ModelTurn> raw = new CompletableFuture<>();
        List<ModelRequest> requests = new ArrayList<>();
        ModelClient noncooperative = (request, events, cancellation) -> {
            requests.add(request);
            return raw;
        };
        CancellationSignal during = new CancellationSignal();
        CompletableFuture<ContextCompactor.Result> running = compactor(noncooperative, budget).compactManually(
                ignored -> "system", history("a".repeat(600), "b".repeat(600)), 2, List.of(),
                "actor:cancel-running", during);
        assertEquals(1, requests.size());
        assertFalse(running.isDone());
        during.cancel();
        assertTrue(running.isCompletedExceptionally());
        CompletionException failure = assertThrows(CompletionException.class, running::join);
        assertEquals("agent_cancelled", ((ModelClientException) failure.getCause()).failure().code());
        assertFalse(raw.isDone());
        assertTrue(raw.complete(textTurn(SUMMARY)));
        assertThrows(CompletionException.class, running::join);
        assertEquals(1, requests.size(), "cancellation must not cause a retry or late checkpoint");
    }

    @Test
    void everyChunkAndRetryUsesCurrentInputBudgetActualOutputCapAndUsageObserver() {
        ContextBudget budget = new ContextBudget(2_200, 256);
        RecordingModel model = new RecordingModel(budget,
                summaryWithGoal("s".repeat(400)), EMPTY_SUMMARY, EMPTY_SUMMARY);
        List<ModelMessage> messages = history("a".repeat(800), "b".repeat(800));
        List<ModelEvent> usage = new ArrayList<>();
        ContextCompactor.Result result = compactor(model, budget).compactManually(
                ignored -> "system", messages, 2, List.of(), "actor:chunks", new CancellationSignal(),
                ImagePayloadResolver.unavailable(), usage::add).join();

        assertTrue(result.successful(), result::failureMessage);
        assertEquals(3, model.requests.size(), "one output-cap retry, then the next complete older unit");
        assertEquals(model.requests.getFirst().messages(), model.requests.get(1).messages());
        assertTrue(model.requests.get(1).systemPrompt().contains("Use fewer, shorter items"));
        assertEquals(3, usage.stream().filter(ModelEvent.UsageStarted.class::isInstance).count());
        assertEquals(3, usage.stream().filter(ModelEvent.UsageObserved.class::isInstance).count());
        assertTrue(usage.stream().allMatch(event -> event instanceof ModelEvent.UsageStarted
                || event instanceof ModelEvent.UsageObserved));
        assertTrue(result.projection().estimatedTokens() <= budget.inputTokens());
        assertEquals(2, result.checkpoint().sourceToIndexExclusive());
    }

    @Test
    void persistentlyOverCapSummaryFailsAfterOneRetryEvenIfFinalContextWouldFit() {
        ContextBudget budget = new ContextBudget(10_000, 256);
        RecordingModel model = new RecordingModel(budget,
                summaryWithGoal("s".repeat(400)), summaryWithGoal("s".repeat(400)));
        ContextCompactor.Result result = compact(model, budget, history("a".repeat(600), "b".repeat(600)), 2);
        assertFalse(result.successful());
        assertEquals("summary_output_over_budget", result.checkpoint().failureCode());
        assertEquals(2, model.requests.size());
        assertEquals(model.requests.getFirst().messages(), model.requests.getLast().messages());
    }

    @Test
    void indivisibleHistoricalSourceAndProtectedSuffixAreNeverAdmittedOverBudget() {
        ContextBudget budget = new ContextBudget(2_200, 256);
        RecordingModel hugeSource = new RecordingModel(budget, EMPTY_SUMMARY);
        ContextCompactor.Result failure = compact(hugeSource, budget,
                history("a".repeat(3_000), "old answer"), 2);
        assertFalse(failure.successful());
        assertEquals("summary_source_unit_over_budget", failure.checkpoint().failureCode());
        assertTrue(hugeSource.requests.isEmpty());

        RecordingModel protectedSuffix = new RecordingModel(budget, EMPTY_SUMMARY);
        List<ModelMessage> messages = List.of(ModelMessage.userText("a".repeat(600)),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("b".repeat(600)))),
                ModelMessage.userText("current " + "q".repeat(2_000)));
        ContextCompactor.Result noOp = compact(protectedSuffix, budget, messages, 2);
        assertOriginal(noOp, messages);
        assertTrue(protectedSuffix.requests.isEmpty());
    }

    @Test
    void nativeImagesRemainActualImageBlocksInSummaryRequestsAndDerivedProjection() {
        ContextBudget budget = new ContextBudget(12_000, 512);
        ContextTokenEstimator mediaEstimator = new ImageCountingEstimator();
        List<ModelRequest> requests = new ArrayList<>();
        ImagePayloadResolver resolver = reference -> new byte[] {1, 2, 3};
        ModelClient model = (request, events, cancellation) -> {
            requests.add(request);
            assertSame(resolver, request.images());
            assertTrue(mediaEstimator.estimate(request.systemPrompt(), request.messages(), request.tools())
                    <= budget.inputTokens());
            return CompletableFuture.completedFuture(textTurn(SUMMARY));
        };
        ModelContent.Image image = new ModelContent.Image(new ImageReference(
                "a".repeat(64), "image/png", 16, 16, 3));
        ModelMessage old = new ModelMessage(ModelRole.USER, List.of(
                new ModelContent.Text("old image question " + "q".repeat(700)), image, image));
        List<ModelMessage> messages = List.of(old,
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("answer " + "a".repeat(700)))),
                ModelMessage.userText("current"));
        ContextCompactor compactor = new ContextCompactor(model, GSON, mediaEstimator, budget,
                "test-model", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        ContextCompactor.Result result = compactor.compactManually(ignored -> "system", messages,
                2, List.of(), "actor:images", new CancellationSignal(), resolver, ignored -> {}).join();

        assertTrue(result.successful(), result::failureMessage);
        assertEquals(ContextProjection.Kind.SUMMARIZED, result.projection().kind());
        assertEquals(List.of(image, image), images(requests.getFirst().messages()));
        assertEquals(List.of(image, image), images(result.projection().messages()));
        assertSame(image, images(result.projection().messages()).getFirst());
        assertEquals(messages.getLast(), result.projection().messages().getLast());
    }

    private static List<ModelMessage> history(String oldQuestion, String oldAnswer) {
        return List.of(ModelMessage.userText(oldQuestion),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text(oldAnswer))),
                ModelMessage.userText("current"));
    }

    private static List<ModelMessage> exchange(String id, String name, String value, boolean error) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("operation", "inspect");
        return List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(id, name, arguments))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(id, new JsonPrimitive(value), error))));
    }

    private static String resultValue(List<ModelMessage> messages, String id) {
        return messages.stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolResult.class::isInstance).map(ModelContent.ToolResult.class::cast)
                .filter(result -> result.toolUseId().equals(id)).findFirst().orElseThrow().value().getAsString();
    }

    private static List<ModelContent.Image> images(List<ModelMessage> messages) {
        return messages.stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.Image.class::isInstance).map(ModelContent.Image.class::cast).toList();
    }

    private static String summaryWithGoal(String goal) {
        JsonObject object = JsonParser.parseString(EMPTY_SUMMARY).getAsJsonObject();
        object.getAsJsonArray("goals").add(goal);
        return object.toString();
    }

    private static ContextCompactor.Result compact(
            RecordingModel model, ContextBudget budget, List<ModelMessage> messages, int protectedFrom) {
        return compactor(model, budget).compactManually(ignored -> "system", messages, protectedFrom,
                List.of(), "actor:manual-test", new CancellationSignal()).join();
    }

    private static ContextCompactor compactor(ModelClient model, ContextBudget budget) {
        return new ContextCompactor(model, GSON, ESTIMATOR, budget,
                "test-model", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private static ModelTurn textTurn(String text) {
        return new ModelTurn("test", "test-model", List.of(new ModelContent.Text(text)), "end_turn", ModelUsage.empty());
    }

    private static void assertOriginal(ContextCompactor.Result result, List<ModelMessage> messages) {
        assertTrue(result.successful());
        assertEquals(ContextProjection.Kind.ORIGINAL, result.projection().kind());
        assertEquals(ModelContextCodec.safe(messages), result.projection().messages());
        assertNull(result.checkpoint(), "an explicit no-op does not create a successful checkpoint");
        assertNull(result.failureCode());
        assertNull(result.failureMessage());
    }

    private static final class RecordingModel implements ModelClient {
        private final ContextBudget budget;
        private final List<String> summaries;
        private final List<ModelRequest> requests = new ArrayList<>();

        private RecordingModel(ContextBudget budget, String... summaries) {
            this.budget = budget;
            this.summaries = List.of(summaries);
        }

        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request, Consumer<ModelEvent> events, CancellationSignal cancellation) {
            requests.add(request);
            int input = ESTIMATOR.estimate(request.systemPrompt(), request.messages(), request.tools());
            assertTrue(input <= budget.inputTokens(), () -> "summary input exceeds budget: " + input);
            assertNotNull(request.maxOutputTokens());
            assertTrue(request.maxOutputTokens() > 0 && request.maxOutputTokens() <= budget.maxOutputTokens());
            assertTrue(requests.size() <= summaries.size(), "unexpected extra summary invocation");
            events.accept(new ModelEvent.TextDelta("summary text must not be user-facing"));
            return CompletableFuture.completedFuture(textTurn(summaries.get(requests.size() - 1)));
        }
    }

    /** The existing byte fixture intentionally models text only; this one counts retained images. */
    private static final class ImageCountingEstimator implements ContextTokenEstimator {
        @Override
        public int estimate(String prompt, List<ModelMessage> messages, List<ModelToolDefinition> tools) {
            List<ModelMessage> textOnly = messages.stream().map(message -> new ModelMessage(message.role(),
                    message.content().stream().filter(content -> !(content instanceof ModelContent.Image)).toList())).toList();
            return ESTIMATOR.estimate(prompt, textOnly, tools) + images(messages).size() * 100;
        }

        @Override
        public int estimateText(String text) { return ESTIMATOR.estimateText(text); }
    }
}
