package dev.openallay.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.agent.context.ContextTokenEstimator;
import dev.openallay.agent.context.Utf8ContextTokenEstimator;
import dev.openallay.agent.session.AgentSessionKey;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.agent.tool.ToolRuntimeCatalog;
import dev.openallay.agent.trace.LiveTraceStore;
import dev.openallay.capability.CapabilityPolicy;
import dev.openallay.capability.ClientCapabilitySnapshot;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.GuideCompactResult;
import dev.openallay.guide.GuidePreparedCompaction;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.model.image.ImageReference;
import dev.openallay.skill.RetainedSkillContext;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Native runtime controls, without invoking the Agent loop, game, or a provider. */
final class ClientGuideRuntimeManualCompactionTest {
    private static final String PROFILE = "default";
    private static final String SUMMARY = """
            {"goals":["build"],"preferences":[],"completedTopics":[],"currentTasks":[],
             "decisions":[],"unresolvedQuestions":[],"evidenceReferences":[]}
            """;
    private static final ContextTokenEstimator ESTIMATOR = new Utf8ContextTokenEstimator();
    private static final ContextBudget BUDGET = new ContextBudget(100_000, 512);

    @Test
    void preparesActualRuntimeHistoryWithoutPublishingUntilDurableCommit() {
        Fixture fixture = new Fixture();
        List<ModelMessage> actual = history();
        fixture.sessions.hydrate(fixture.key, actual);
        List<AgentEvent> events = new ArrayList<>();
        GuidePreparedCompaction prepared = success(fixture.prepare(
                List.of(ModelMessage.userText("not actual history")), new CancellationSignal(), events::add).join());

        assertEquals(actual, prepared.source());
        assertEquals(GuideCompactResult.Status.COMPACTED, prepared.outcome().status());
        assertTrue(prepared.current());
        assertEquals(1, fixture.model.requests.size());
        assertFalse(fixture.model.requests.getFirst().stream());
        assertTrue(fixture.model.requests.getFirst().tools().isEmpty());
        assertEquals(2, prepared.outcome().checkpoint().sourceToIndexExclusive());
        assertEquals(actual.subList(2, actual.size()), prepared.projection().subList(1, prepared.projection().size()));
        assertTrue(prepared.outcome().afterTokens() < prepared.outcome().beforeTokens());
        assertEquals(BUDGET.inputTokens(), prepared.outcome().inputBudget());
        assertTrue(events.stream().allMatch(event -> event instanceof AgentEvent.ModelUsageStarted
                || event instanceof AgentEvent.ModelUsageObserved));
        assertEquals(1, events.stream().filter(AgentEvent.ModelUsageStarted.class::isInstance).count());
        assertEquals(1, events.stream().filter(AgentEvent.ModelUsageObserved.class::isInstance).count());
        assertFalse(fixture.sessions.status(fixture.key).active(), "control is not an Agent request");
        assertEquals(actual.size(), fixture.sessions.status(fixture.key).historyMessages());
        assertInstanceOf(ToolResult.Failure.class, fixture.sessions.reserve(fixture.key, UUID.randomUUID()));
        assertTrue(fixture.runtime.contextEstimate(PROFILE, fixture.actor, "main").isEmpty());

        assertTrue(prepared.publish());
        assertFalse(prepared.current());
        assertFalse(prepared.publish());
        prepared.close();
        AgentSessionStore.Lease next = success(fixture.sessions.reserve(fixture.key, UUID.randomUUID()));
        assertEquals(prepared.projection(), next.history());
        assertTrue(next.checkpoints().isEmpty(), "manual control does not emit or install checkpoint events");
        assertTrue(fixture.runtime.contextEstimate(PROFILE, fixture.actor, "main").isEmpty(),
                "a control ID is not a player request estimate identity");
    }

    @Test
    void durableSeedDoesNotHydrateActualContextDuringPrepareAndClose() {
        Fixture fixture = new Fixture();
        GuidePreparedCompaction prepared = success(fixture.prepare(history(), new CancellationSignal(), ignored -> {}).join());
        assertEquals(history(), prepared.source());
        assertFalse(fixture.runtime.hasContext(fixture.actor, "main"));
        prepared.close();
        AgentSessionStore.Lease next = success(fixture.sessions.reserve(fixture.key, UUID.randomUUID()));
        assertTrue(next.history().isEmpty(), "aborted control must not publish even its durable seed");
    }

    @Test
    void noOpRetainsOriginalHistoryAndOriginalSkillFactsWithoutCallingModel() {
        for (List<ModelMessage> history : List.of(List.<ModelMessage>of(), List.of(
                ModelMessage.userText("newest question"), text("newest answer")), List.of(
                ModelMessage.userText("[OpenAllay derived conversation memory; NOT factual evidence]\n"
                        + "m".repeat(2_000)), ModelMessage.userText("newest"), text("answer")))) {
            Fixture fixture = new Fixture();
            fixture.sessions.hydrate(fixture.key, history);
            AgentSessionStore.Lease previous = success(fixture.sessions.reserve(fixture.key, UUID.randomUUID()));
            RetainedSkillContext originalSkills = previous.retainedSkills();
            fixture.sessions.finish(previous, history);
            GuidePreparedCompaction prepared = success(fixture.prepare(List.of(), new CancellationSignal(), ignored -> {}).join());
            assertEquals(GuideCompactResult.Status.NOT_NEEDED, prepared.outcome().status());
            assertEquals(history, prepared.projection());
            assertTrue(fixture.model.requests.isEmpty());
            assertTrue(prepared.publish());
            AgentSessionStore.Lease next = success(fixture.sessions.reserve(fixture.key, UUID.randomUUID()));
            assertEquals(history, next.history());
            assertSame(originalSkills, next.retainedSkills());
        }
    }

    @Test
    void cancellationReleasesControlImmediatelyAndRejectsNoncooperativeLateSummary() {
        Fixture fixture = new Fixture();
        fixture.sessions.hydrate(fixture.key, history());
        fixture.model.pending = new CompletableFuture<>();
        CancellationSignal cancellation = new CancellationSignal();
        List<AgentEvent> usage = new ArrayList<>();
        var preparing = fixture.prepare(List.of(), cancellation, usage::add);
        assertFalse(preparing.isDone());
        assertEquals(1, fixture.model.requests.size());
        cancellation.cancel();
        ToolResult.Failure<GuidePreparedCompaction> failure = failure(preparing.join());
        assertEquals("compact_cancelled", failure.code());
        AgentSessionStore.Lease next = success(fixture.sessions.reserve(fixture.key, UUID.randomUUID()));
        assertEquals(history(), next.history());
        fixture.model.pending.complete(turn(SUMMARY));
        assertEquals(history(), next.history());
        assertUsagePair(usage);
        assertEquals(1, fixture.skills.closedSkills);
        assertEquals(0, fixture.skills.closedRequests);
        assertEquals(0, fixture.skills.executions);
    }

    @Test
    void clearSessionInvalidatesPreparedPublicationAndCannotResurrectContext() {
        Fixture fixture = new Fixture();
        fixture.sessions.hydrate(fixture.key, history());
        GuidePreparedCompaction prepared = success(fixture.prepare(List.of(), new CancellationSignal(), ignored -> {}).join());
        fixture.runtime.clearSession(fixture.actor, "main");
        assertFalse(prepared.current());
        assertFalse(prepared.publish());
        prepared.close();
        assertFalse(fixture.runtime.hasContext(fixture.actor, "main"));
        assertTrue(success(fixture.sessions.reserve(fixture.key, UUID.randomUUID())).history().isEmpty());
    }

    @Test
    void malformedSummaryFailsWithoutChangingOriginalHistoryOrRetainedSkills() {
        Fixture fixture = new Fixture();
        fixture.model.response = "not valid summary";
        fixture.sessions.hydrate(fixture.key, history());
        AgentSessionStore.Lease previous = success(fixture.sessions.reserve(fixture.key, UUID.randomUUID()));
        fixture.sessions.finish(previous, history());
        List<AgentEvent> usage = new ArrayList<>();
        ToolResult.Failure<GuidePreparedCompaction> failure = failure(fixture.prepare(
                List.of(), new CancellationSignal(), usage::add).join());
        assertEquals("context_compaction_failed", failure.code());
        assertUsagePair(usage);
        AgentSessionStore.Lease next = success(fixture.sessions.reserve(fixture.key, UUID.randomUUID()));
        assertEquals(history(), next.history());
        assertSame(previous.retainedSkills(), next.retainedSkills());
        assertNotSame(previous.retainedSkills(), fixture.skills.retained);
        assertTrue(fixture.skills.preparedSystem);
        assertTrue(fixture.skills.preparedContext);
        assertEquals(1, fixture.skills.closedSkills);
        assertEquals(0, fixture.skills.closedRequests);
        assertEquals(0, fixture.skills.executions);
    }

    @Test
    void newestUnansweredQuestionAlsoRetainsNewestCompletedTurnAndExactToolExchange() {
        Fixture fixture = new Fixture();
        String largeResult = "r".repeat(2_000);
        List<ModelMessage> actual = List.of(ModelMessage.userText("older " + "q".repeat(800)),
                text("older answer " + "a".repeat(800)), ModelMessage.userText("latest completed question"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        "recent", "test__fact", new JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "recent", new JsonPrimitive(largeResult), false))),
                text("terminal answer"), ModelMessage.userText("new unanswered question"));
        fixture.sessions.hydrate(fixture.key, actual);
        GuidePreparedCompaction prepared = success(fixture.prepare(List.of(), new CancellationSignal(), ignored -> {}).join());
        assertEquals(GuideCompactResult.Status.COMPACTED, prepared.outcome().status());
        assertEquals(2, prepared.outcome().checkpoint().sourceToIndexExclusive());
        assertEquals(actual.subList(2, actual.size()), prepared.projection().subList(1, prepared.projection().size()));
        prepared.close();
        assertEquals(actual, success(fixture.sessions.reserve(fixture.key, UUID.randomUUID())).history());
    }

    @Test
    void imageInputUsesCapturedActorResolverAndRecentImageQuestionIsProtected() throws java.io.IOException {
        Fixture fixture = new Fixture();
        ImageReference old = new ImageReference("a".repeat(64), "image/png", 1, 1, 1);
        ImageReference recent = new ImageReference("b".repeat(64), "image/jpeg", 1, 1, 1);
        List<ModelMessage> actual = List.of(new ModelMessage(ModelRole.USER, List.of(
                new ModelContent.Text("older " + "q".repeat(800)), new ModelContent.Image(old))),
                text("older answer " + "a".repeat(800)), new ModelMessage(ModelRole.USER,
                        List.of(new ModelContent.Image(recent))), text("recent visual answer"));
        fixture.sessions.hydrate(fixture.key, actual);
        byte[] payload = {7};
        ImagePayloadResolver images = reference -> {
            assertEquals(old, reference);
            return payload;
        };
        GuidePreparedCompaction prepared = success(fixture.runtime.prepareCompaction(PROFILE,
                fixture.actor, "main", UUID.randomUUID(), List.of(), new CancellationSignal(),
                images, ignored -> {}).join());
        assertSame(images, fixture.model.requests.getFirst().images());
        assertArrayEquals(payload, fixture.model.requests.getFirst().images().read(old));
        assertEquals(actual.subList(2, actual.size()), prepared.projection().subList(1, prepared.projection().size()));
        assertTrue(prepared.projection().getFirst().content().contains(new ModelContent.Image(old)));
        prepared.close();
    }

    @Test
    void selectedProfileImageCapabilityRejectsActualImagesBeforeAnySummaryDispatch() {
        for (var capability : List.of(dev.openallay.model.image.ImageInputCapability.UNSUPPORTED,
                dev.openallay.model.image.ImageInputCapability.UNKNOWN)) {
            Fixture fixture = new Fixture();
            ImageReference image = new ImageReference("c".repeat(64), "image/png", 1, 1, 1);
            List<ModelMessage> actual = List.of(new ModelMessage(ModelRole.USER, List.of(
                    new ModelContent.Image(image))), text("actual answer"));
            fixture.sessions.hydrate(fixture.key, actual);
            ToolResult.Failure<GuidePreparedCompaction> failure = failure(fixture.runtime.prepareCompaction(
                    PROFILE, fixture.actor, "main", UUID.randomUUID(), List.of(), new CancellationSignal(),
                    ImagePayloadResolver.unavailable(), ignored -> {}, capability).join());
            assertEquals(capability == dev.openallay.model.image.ImageInputCapability.UNKNOWN
                    ? "image_input_unknown" : "image_input_unsupported", failure.code());
            assertTrue(fixture.model.requests.isEmpty());
            assertEquals(actual, success(fixture.sessions.reserve(fixture.key, UUID.randomUUID())).history());
        }
    }

    @Test
    void unavailableBudgetAndBusyLeaseNeverCallModelOrCreateAgentRequest() {
        Fixture fixture = new Fixture();
        ClientGuideRuntime unavailable = new ClientGuideRuntime(fixture.model, fixture.sessions, dev.openallay.json.EngineJson.create(),
                Runnable::run, fixture.skills, new LiveTraceStore(null), null, null,
                capabilities(), ESTIMATOR);
        assertFalse(unavailable.compactAvailable(PROFILE));
        assertEquals("compact_unavailable", failure(unavailable.prepareCompaction(PROFILE,
                fixture.actor, "main", UUID.randomUUID(), history(), new CancellationSignal(), ignored -> {}).join()).code());
        assertFalse(fixture.runtime.compactAvailable("missing"));
        assertSame(fixture.runtime, fixture.runtime.compactIdentity(PROFILE));
        AgentSessionStore.Lease active = success(fixture.sessions.reserve(fixture.key, UUID.randomUUID()));
        assertEquals("compact_busy", failure(fixture.prepare(history(), new CancellationSignal(), ignored -> {}).join()).code());
        assertEquals(active.requestId(), fixture.sessions.status(fixture.key).requestId());
        assertTrue(fixture.model.requests.isEmpty());
    }

    private static void assertUsagePair(List<AgentEvent> usage) {
        assertEquals(2, usage.size());
        AgentEvent.ModelUsageStarted started = assertInstanceOf(AgentEvent.ModelUsageStarted.class, usage.getFirst());
        AgentEvent.ModelUsageObserved observed = assertInstanceOf(AgentEvent.ModelUsageObserved.class, usage.getLast());
        assertEquals(started.callId(), observed.callId());
        assertEquals("fixture-model", started.modelIdentifier());
        assertEquals("fixture-model", observed.modelIdentifier());
    }

    private static List<ModelMessage> history() {
        return List.of(ModelMessage.userText("older " + "q".repeat(800)),
                text("older answer " + "a".repeat(800)),
                ModelMessage.userText("newest question"), text("newest answer"));
    }

    private static ModelMessage text(String text) {
        return new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text(text)));
    }

    private static ModelTurn turn(String text) {
        return new ModelTurn("test", "fixture-model", List.of(new ModelContent.Text(text)),
                "stop", ModelUsage.empty());
    }

    @SuppressWarnings("unchecked")
    private static <T> T success(ToolResult<T> result) {
        assertInstanceOf(ToolResult.Success.class, result);
        return ((ToolResult.Success<T>) result).value();
    }

    @SuppressWarnings("unchecked")
    private static <T> ToolResult.Failure<T> failure(ToolResult<T> result) {
        assertInstanceOf(ToolResult.Failure.class, result);
        return (ToolResult.Failure<T>) result;
    }

    private static ClientCapabilitySnapshot capabilities() {
        return new ClientCapabilitySnapshot(CapabilityPolicy.defaults(), ToolRuntimeCatalog.empty(),
                new SkillRepository(new SkillParser(), List.of()).snapshot(Set.of()), Set.of());
    }

    private static final class Fixture {
        private final UUID actor = UUID.randomUUID();
        private final AgentSessionKey key = new AgentSessionKey(actor, "main");
        private final AgentSessionStore sessions = new AgentSessionStore();
        private final RecordingModel model = new RecordingModel();
        private final RecordingSkills skills = new RecordingSkills();
        private final ClientGuideRuntime runtime = new ClientGuideRuntime(model, sessions, dev.openallay.json.EngineJson.create(),
                Runnable::run, skills, new LiveTraceStore(null), BUDGET, "fixture-model", capabilities(), ESTIMATOR);

        private CompletableFuture<ToolResult<GuidePreparedCompaction>> prepare(List<ModelMessage> seed,
                CancellationSignal cancellation, Consumer<AgentEvent> usage) {
            return runtime.prepareCompaction(PROFILE, actor, "main", UUID.randomUUID(), seed,
                    cancellation, ImagePayloadResolver.unavailable(), usage);
        }
    }

    private static final class RecordingModel implements ModelClient {
        private final List<ModelRequest> requests = new ArrayList<>();
        private CompletableFuture<ModelTurn> pending;
        private String response = SUMMARY;

        @Override
        public CompletableFuture<ModelTurn> complete(ModelRequest request, Consumer<ModelEvent> events,
                CancellationSignal cancellation) {
            requests.add(request);
            events.accept(new ModelEvent.TextDelta("summary progress must not become chat text"));
            return pending == null ? CompletableFuture.completedFuture(turn(response)) : pending;
        }
    }

    private static final class RecordingSkills implements AgentToolExecutor {
        private RetainedSkillContext retained;
        private boolean preparedSystem;
        private boolean preparedContext;
        private int closedSkills;
        private int closedRequests;
        private int executions;

        @Override public List<ModelToolDefinition> definitions() { return List.of(); }
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public CompletableFuture<AgentToolResult> execute(String name, JsonObject arguments,
                ToolInvocationContext context, CancellationSignal cancellation) {
            executions++;
            throw new AssertionError("manual control never executes a Tool");
        }
        @Override public String skillSystemPrompt(String prompt) { return prompt + "\ninline fixture Skill"; }
        @Override public void prepareSystem(String prompt, RetainedSkillContext retained) {
            assertTrue(prompt.contains("inline fixture Skill"));
            this.retained = retained;
            preparedSystem = true;
        }
        @Override public List<ModelMessage> refreshContext(List<ModelMessage> messages, RetainedSkillContext retained) {
            assertSame(this.retained, retained);
            return messages;
        }
        @Override public void prepareContext(String correlation, List<ModelMessage> messages, RetainedSkillContext retained) {
            assertSame(this.retained, retained);
            preparedContext = true;
        }
        @Override public String skillManifest(String correlation) { return "fixture Skill fact manifest"; }
        @Override public void closeSkillContext(String correlation) { closedSkills++; }
        @Override public void closeRequestScope(String correlation) { closedRequests++; }
    }
}
