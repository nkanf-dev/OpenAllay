package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.history.*;
import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideServicePresentationTest {
    private final UUID actor = UUID.randomUUID();
    private final FakeLocal local = new FakeLocal();
    private final List<GuidePresentationEvent> observed = new ArrayList<>();
    private final List<String> order = new ArrayList<>();

    @Test void managerBindsBeforeFirstSynchronousRequestCallbackEvenWithoutHudOrScreen() {
        local.immediate = true;
        GuideServiceManager manager = manager(Runnable::run, null);
        manager.listenPresentation(listener());
        assertNull(manager.current(), "registering the sink must not create an agent/service");
        GuideService service = manager.forActor(actor);
        UUID request = success(service.ask("fast task").join());
        assertEquals(List.of("bound", "REPLY_FINAL", "TASK_COMPLETED"), order);
        assertEquals(request, observed.getFirst().key().requestId());
        assertEquals("instant result", observed.getFirst().preview());
        assertEquals(2, observed.size());
        local.emit(request, new AgentEvent.FinalText("duplicate"));
        assertEquals(2, observed.size());
    }

    @Test void queuedSourceSubscribeIsImmediateNotSubjectToDispatcherBindingGap() {
        QueueDispatcher queue = new QueueDispatcher();
        GuideService service = manager(queue, null).forActor(actor);
        service.subscribePresentation(observed::add);
        CompletableFuture<ToolResult<UUID>> asking = service.ask("queued task");
        queue.drain(); UUID request = success(asking.join());
        local.emit(request, new AgentEvent.FinalText("queued result"));
        queue.drain(); assertEquals(2, observed.size());
    }

    @Test void contextUsageSelectionAndForkInheritedDataCannotCreateNewResults() {
        GuideService service = manager(Runnable::run, null).forActor(actor);
        service.subscribePresentation(observed::add);
        UUID request = success(service.ask("source task").join());
        UUID call = UUID.randomUUID();
        local.emit(request, new AgentEvent.ModelUsageStarted(call, "fixture-model"));
        List<ModelMessage> completedContext = List.of(ModelMessage.userText("source task"),
                new ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                        List.of(new dev.openallay.model.ModelContent.Text("source result"))));
        local.emit(request, new AgentEvent.ContextUpdated(completedContext, completedContext));
        assertTrue(observed.isEmpty(), "context and call-start observations are not player result receipts");
        local.emit(request, new AgentEvent.ModelUsageObserved(call, "fixture-model",
                new dev.openallay.model.ModelUsage(7, 2, 0)));
        assertTrue(observed.isEmpty(), "a settled numeric call does not create a result receipt");
        local.finish(request, "source result");
        assertEquals(2, observed.size());
        assertEquals(1, service.telemetry().sessionUsage().actualCalls());
        assertNull(service.snapshot().sessions().getFirst().workingRequestId(),
                "fork requires actual endpoint cleanup and all numeric call receipts settled");
        assertEquals("branch", success(service.forkSession("main", request, "branch").join()));
        assertEquals("branch", service.snapshot().selectedSession());
        assertFalse(service.snapshot().sessions().stream().filter(session -> session.sessionId().equals("branch"))
                .findFirst().orElseThrow().requests().isEmpty());
        service.selectSession("main").join(); service.selectSession("branch").join();
        assertEquals(2, observed.size(), "inherited fork rows and session selection are not live receipts");
        UUID branch = success(service.ask("new branch task").join()); local.finish(branch, "new branch result");
        assertEquals(4, observed.size());
        assertEquals("branch", observed.getLast().key().sessionId());
        assertNotEquals(observed.getFirst().key().sessionOwner(), observed.getLast().key().sessionOwner());
    }

    @Test void cancelAndDisconnectInvalidateBeforeSyntheticFailureAndFenceOldActorCallbacks() {
        GuideServiceManager manager = manager(Runnable::run, null); manager.listenPresentation(listener());
        GuideService first = manager.forActor(actor);
        UUID cancelled = success(first.ask("cancelled").join());
        assertTrue(success(first.cancel().join()));
        local.emit(cancelled, new AgentEvent.FinalText("late after cancel"));
        assertTrue(observed.isEmpty());
        ToolResult.Failure<UUID> beforeRelease = assertInstanceOf(ToolResult.Failure.class,
                first.ask("before actual cleanup").join());
        assertEquals("agent_busy", beforeRelease.code(), "visible cancellation is not endpoint cleanup");
        local.releaseCancelled(cancelled, "cancelled");
        assertTrue(observed.isEmpty(), "final cancelled-context handoff and release are non-notifying");
        assertNull(first.snapshot().sessions().getFirst().workingRequestId());
        UUID pending = success(first.ask("disconnect pending").join());
        UUID generation = first.presentationGeneration();
        manager.disconnect().join();
        assertEquals(List.of("bound", "invalidated"), order);
        assertNull(manager.current()); assertTrue(observed.isEmpty());
        local.emit(pending, new AgentEvent.FinalText("late after disconnect"));
        GuideService second = manager.forActor(actor);
        assertNotEquals(generation, second.presentationGeneration());
        local.emit(pending, new AgentEvent.Failed("late", "old generation"));
        assertTrue(observed.isEmpty());
        UUID current = success(second.ask("new task").join()); local.finish(current, "new result");
        assertEquals(2, observed.size());
        assertEquals(second.presentationGeneration(), observed.getFirst().key().connectionGeneration());
        assertEquals(1, observed.getFirst().key().sequence());
    }

    @Test void metadataRestorePagingAndContextPreloadAreNeverNewReplies() {
        FakeHistory history = new FakeHistory(actor);
        GuideServiceManager manager = manager(Runnable::run, history); manager.listenPresentation(listener());
        GuideService service = manager.forActor(actor);
        assertTrue(observed.isEmpty());
        ToolResult<GuideHistoryPage> page = service.requestHistoryWindow("main",
                GuideHistoryPageRequest.Direction.NEWEST, null, 10).join();
        assertInstanceOf(ToolResult.Success.class, page);
        assertEquals("loaded final result", service.snapshot().sessions().getFirst().requests().getFirst().assistantText());
        assertTrue(observed.isEmpty());
        local.context = true;
        UUID live = success(service.ask("after restore").join());
        assertEquals(1, history.contextLoads);
        assertTrue(observed.isEmpty(), "context preload must not synthesize loaded replies");
        local.finish(live, "actual new live result");
        assertEquals(2, observed.size());
        service.requestHistoryWindow("main", GuideHistoryPageRequest.Direction.NEWEST, null, 10).join();
        assertEquals(2, observed.size(), "paging after live completion does not replay terminal rows");
    }

    @Test void failedAdmissionDoesNotCreateReceiptAndSessionRecreationGetsNewOwner() {
        GuideService service = manager(Runnable::run, null).forActor(actor); service.subscribePresentation(observed::add);
        assertInstanceOf(ToolResult.Failure.class, service.ask((ModelMessage) null).join()); assertTrue(observed.isEmpty());
        service.selectSession("replaceable").join(); UUID firstOwner = service.presentationSessionOwner("replaceable").orElseThrow();
        service.closeSession("replaceable").join();
        service.selectSession("replaceable").join();
        assertNotEquals(firstOwner, service.presentationSessionOwner("replaceable").orElseThrow());
        assertTrue(observed.isEmpty());
    }

    private GuidePresentationListener listener() {
        return new GuidePresentationListener() {
            public void bound(GuideService service) { order.add("bound"); }
            public void event(GuidePresentationEvent event) { observed.add(event); order.add(event.kind().name()); }
            public void invalidated(UUID generation) { order.add("invalidated"); }
        };
    }
    private GuideServiceManager manager(ClientEventDispatcher dispatcher, FakeHistory history) {
        return new GuideServiceManager(local, new FakeRemote(),
                (caps, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                dispatcher, Clock.systemUTC(), new Gson(), history, history == null ? null : ignored -> history.scope);
    }
    private static <T> T success(ToolResult<T> result) {
        return ((ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result,
                () -> "Expected accepted request/fork, got " + result)).value();
    }
    private static final class FakeRemote implements GuideRemoteEndpoint {
        public boolean serverModelAvailable() { return false; }
        public boolean serverToolsAvailable() { return false; }
        public boolean ask(UUID request, String session, String question, Consumer<AgentEvent> events) { return false; }
        public boolean cancel(UUID request) { return false; }
        public void disconnect() {}
    }
    private static final class FakeLocal implements GuideLocalEndpoint {
        final Map<UUID, Consumer<AgentEvent>> events = new LinkedHashMap<>();
        final Map<UUID, CompletableFuture<AgentResult>> futures = new LinkedHashMap<>();
        boolean immediate, context;
        public Set<ContextCapability> requiredContext() { return Set.of(); }
        public Optional<GuideContextSpec> contextSpec(String profile) {
            return context ? Optional.of(new GuideContextSpec(new ContextBudget(4096, 256), 100, "fixture-model")) : Optional.empty();
        }
        public CompletableFuture<AgentResult> ask(UUID actor, String session, UUID request, String question,
                                                  ToolInvocationContext captured, Consumer<AgentEvent> sink) {
            events.put(request, sink);
            CompletableFuture<AgentResult> future = new CompletableFuture<>(); futures.put(request, future);
            if (immediate) finish(request, "instant result");
            return future;
        }
        void emit(UUID request, AgentEvent event) { events.get(request).accept(event); }
        void finish(UUID request, String text) {
            emit(request, new AgentEvent.FinalText(text));
            futures.get(request).complete(new AgentResult(AgentState.COMPLETED, text, null, null, null));
        }
        void releaseCancelled(UUID request, String question) {
            List<ModelMessage> cancelledContext = List.of(ModelMessage.userText(question),
                    new ModelMessage(dev.openallay.model.ModelRole.ASSISTANT, List.of(
                            new dev.openallay.model.ModelContent.Text(
                                    "[OpenAllay request ended: agent_cancelled] Agent request was cancelled"))));
            emit(request, new AgentEvent.ContextFinalized(cancelledContext, cancelledContext));
            futures.get(request).complete(new AgentResult(AgentState.CANCELLED, "",
                    "agent_cancelled", "Agent request was cancelled", null));
        }
        public boolean cancel(UUID actor, String session) { return true; }
        public void clearSession(UUID actor, String session) {}
        public void clearActor(UUID actor) {}
    }
    private static final class QueueDispatcher implements ClientEventDispatcher {
        final ArrayDeque<Runnable> queued = new ArrayDeque<>();
        public void execute(Runnable callback) { queued.add(callback); }
        void drain() { while (!queued.isEmpty()) queued.remove().run(); }
    }
    private static final class FakeHistory implements GuideHistoryAccess {
        final GuideHistoryScope scope;
        final GuideRequestSnapshot restored;
        final GuideHistoryCursor cursor;
        int contextLoads;
        FakeHistory(UUID actor) {
            scope = GuideHistoryScope.derive(actor, GuideHistoryScope.Kind.SINGLEPLAYER, "fixture-world");
            Instant now = Instant.parse("2026-10-01T00:00:00Z");
            GuideRequestSnapshot start = GuideRequestSnapshot.start(UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "loaded question", now);
            restored = new GuideStateReducer(new Gson()).apply(start, new AgentEvent.FinalText("loaded final result"), now);
            cursor = new GuideHistoryCursor(0, restored.requestId());
        }
        public CompletableFuture<Optional<GuideHistoryMetadata>> metadata(GuideHistoryScope ignored) {
            return CompletableFuture.completedFuture(Optional.of(new GuideHistoryMetadata(scope, "main", List.of(
                    new GuideHistoryMetadata.Session("main", 0, GuideModelSelection.client("default"), 1, cursor, cursor)), Instant.EPOCH)));
        }
        public CompletableFuture<GuideHistoryPage> page(GuideHistoryPageRequest request) {
            return CompletableFuture.completedFuture(new GuideHistoryPage("main", List.of(restored), cursor, cursor, false, false));
        }
        public CompletableFuture<GuideHistoryContextSeed> context(GuideHistoryContextRequest request) {
            contextLoads++;
            return CompletableFuture.completedFuture(new GuideHistoryContextSeed("main", List.of(ModelMessage.userText("stored context")), List.of(), 10));
        }
        public CompletableFuture<List<ModelMessage>> requestContext(GuideHistoryScope scope, UUID request) { return CompletableFuture.completedFuture(List.of()); }
        public CompletableFuture<Void> commit(GuideHistoryCommit commit) { return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Void> delete(GuideHistoryDeleteScope scope) { return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Void> resetDatabase() { return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Void> flush() { return CompletableFuture.completedFuture(null); }
        public GuideHistoryActivity activity() { return new GuideHistoryActivity(0, false); }
    }
}
