package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.history.GuideHistoryAccess;
import dev.openallay.guide.history.GuideHistoryActivity;
import dev.openallay.guide.history.GuideHistoryCommit;
import dev.openallay.guide.history.GuideHistoryDeleteScope;
import dev.openallay.guide.history.GuideHistoryMetadata;
import dev.openallay.guide.history.GuideHistoryScope;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideServiceManagerHistoryTest {
    private static final UUID ACTOR =
            UUID.fromString("77a70151-0279-4ee6-a6b9-b15c24d08f05");

    @Test
    void waitsForPreviousDisconnectBeforeLoadingReplacementScope() throws Exception {
        QueuedDispatcher dispatcher = new QueuedDispatcher();
        RecordingHistory history = new RecordingHistory();
        GuideHistoryScope first = GuideHistoryScope.derive(
                ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "first.example");
        GuideHistoryScope second = GuideHistoryScope.derive(
                ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "second.example");
        GuideHistoryScope[] selected = {first};
        GuideServiceManager manager = new GuideServiceManager(
                new IdleLocal(),
                new IdleRemote(),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                dispatcher,
                Clock.systemUTC(),
                new Gson(),
                history,
                actor -> selected[0]);

        manager.forActor(ACTOR);
        assertEquals(List.of(first), history.loads);
        history.flushGate = new CompletableFuture<>();
        selected[0] = second;

        GuideService replacement = manager.forActor(ACTOR);

        assertEquals(GuidePersistenceSnapshot.State.LOADING,
                replacement.snapshot().persistence().state());
        assertEquals(List.of(first), history.loads);
        dispatcher.runAll();
        assertEquals(List.of(first), history.loads, "old durable flush must complete before the replacement loads");
        assertFalse(history.secondLoad.isDone());
        dispatcher.armWorkerHandoff();
        history.flushGate.complete(null);
        history.secondLoad.get(2, TimeUnit.SECONDS);
        dispatcher.awaitWorkerHandoff();
        dispatcher.runAll();
        assertEquals(List.of(first, second), history.loads);
        assertEquals(GuidePersistenceSnapshot.State.AVAILABLE,
                replacement.snapshot().persistence().state());
    }

    @Test
    void managerResetUsesCurrentServiceGateAndResetsOnlyAfterCommit() throws Exception {
        RecordingHistory history = new RecordingHistory();
        GuideHistoryScope scope = GuideHistoryScope.derive(
                ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "reset.example");
        GuideServiceManager manager = new GuideServiceManager(
                new IdleLocal(),
                new IdleRemote(),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run,
                Clock.systemUTC(),
                new Gson(),
                history,
                actor -> scope);
        GuideService service = manager.forActor(ACTOR);
        service.selectSession("other").get(2, TimeUnit.SECONDS);

        CompletableFuture<ToolResult<Boolean>> resetting = manager.resetHistoryDatabase();

        assertEquals(1, history.resetCalls);
        assertFalse(resetting.isDone());
        assertFailure(service.ask("during reset").get(2, TimeUnit.SECONDS), "history_delete_busy");
        history.reset.complete(null);

        ToolResult.Success<?> resetResult = assertInstanceOf(ToolResult.Success.class,
                resetting.get(2, TimeUnit.SECONDS));
        assertEquals(Boolean.TRUE, resetResult.value());
        assertEquals(List.of("main"), service.snapshot().sessions().stream()
                .map(GuideSessionSnapshot::sessionId).toList());
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
    }

    @Test
    void managerResetWithoutCurrentServiceIsUnavailable() throws Exception {
        GuideServiceManager manager = new GuideServiceManager(
                new IdleLocal(),
                new IdleRemote(),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run,
                Clock.systemUTC(),
                new Gson());

        assertFailure(manager.resetHistoryDatabase().get(2, TimeUnit.SECONDS), "history_unavailable");
    }

    @Test
    void settingsSnapshotExposesOnlyFriendlyScopeAndCurrentGuideState() {
        RecordingHistory history = new RecordingHistory();
        GuideHistoryScope scope = GuideHistoryScope.derive(
                ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "private.example");
        GuideServiceManager manager = new GuideServiceManager(
                new IdleLocal(),
                new IdleRemote(),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run,
                Clock.systemUTC(),
                new Gson(),
                history,
                actor -> scope);

        GuideHistorySettingsSnapshot disconnected = manager.historySettingsSnapshot();
        assertTrue(disconnected.configured());
        assertTrue(disconnected.guide().isEmpty());
        assertTrue(disconnected.scopeKind().isEmpty());

        manager.forActor(ACTOR);
        GuideHistorySettingsSnapshot connected = manager.historySettingsSnapshot();

        assertEquals(ACTOR, connected.guide().orElseThrow().actorId());
        assertEquals(GuideHistoryScope.Kind.MULTIPLAYER,
                connected.scopeKind().orElseThrow());
        assertFalse(connected.toString().contains("private.example"));
    }

    @Test
    void connectionScopeChangesInvalidatePublishedContextSourcesBeforeNewActorReadsThem() throws Exception {
        var knowledge = new dev.openallay.knowledge.KnowledgeRegistry();
        int[] clears = {0};
        GuideContextProvider contexts = new GuideContextProvider() {
            @Override public ToolResult<ToolInvocationContext> capture(
                    Set<ContextCapability> capabilities, String correlation) {
                return new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation));
            }
            @Override public void clearConnectionState() { clears[0]++; knowledge.clearConnectionState(); }
        };
        GuideHistoryScope[] selected = {GuideHistoryScope.derive(
                ACTOR, GuideHistoryScope.Kind.SINGLEPLAYER, "world-a")};
        GuideServiceManager manager = new GuideServiceManager(new IdleLocal(), new IdleRemote(),
                contexts, Runnable::run, Clock.systemUTC(), new Gson(), new RecordingHistory(),
                actor -> selected[0]);
        manager.forActor(ACTOR);
        assertEquals(1, clears[0]);
        assertTrue(knowledge.reload(List.of(new dev.openallay.knowledge.KnowledgeSourceProvider() {
            @Override public String sourceId() { return "patchouli"; }
            @Override public dev.openallay.knowledge.KnowledgeLoad load() {
                var evidence = new dev.openallay.context.EvidenceMetadata(
                        dev.openallay.context.DataAuthority.RESOURCE_ASSET,
                        dev.openallay.context.DataCompleteness.COMPLETE,
                        java.time.Instant.EPOCH, "patchouli:resources", "patchouli:parser",
                        "fixture", "fixture", java.util.Map.of());
                return new dev.openallay.knowledge.KnowledgeLoad(List.of(), List.of(), List.of(evidence));
            }
        })));
        assertTrue(knowledge.sourceSnapshot().loaded());
        selected[0] = GuideHistoryScope.derive(ACTOR, GuideHistoryScope.Kind.SINGLEPLAYER, "world-b");
        manager.forActor(ACTOR);
        assertEquals(2, clears[0]);
        assertFalse(knowledge.sourceSnapshot().loaded());
        manager.disconnect().get(2, TimeUnit.SECONDS);
        assertEquals(3, clears[0]);
        assertFalse(knowledge.sourceSnapshot().loaded());
        manager.disconnect().get(2, TimeUnit.SECONDS);
        assertEquals(4, clears[0]);
    }

    private static void assertFailure(ToolResult<?> result, String code) {
        assertEquals(code,
                ((ToolResult.Failure<?>) assertInstanceOf(ToolResult.Failure.class, result)).code());
    }

    private static final class QueuedDispatcher implements dev.openallay.client.ClientEventDispatcher {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();
        private final Thread owner = Thread.currentThread();
        private volatile CountDownLatch workerHandoff;

        @Override
        public void execute(Runnable event) {
            synchronized (queued) {
                queued.add(event);
            }
            CountDownLatch handoff = workerHandoff;
            if (Thread.currentThread() != owner && handoff != null) {
                handoff.countDown();
            }
        }

        private void armWorkerHandoff() {
            assertTrue(workerHandoff == null || workerHandoff.getCount() == 0,
                    "the previous worker handoff must complete before rearming");
            workerHandoff = new CountDownLatch(1);
        }

        private void awaitWorkerHandoff() throws InterruptedException {
            assertTrue(workerHandoff.await(2, TimeUnit.SECONDS),
                    "the cleanup worker must enqueue the replacement history completion");
        }

        private void runAll() {
            while (true) {
                Runnable event;
                synchronized (queued) {
                    if (queued.isEmpty()) return;
                    event = queued.remove();
                }
                event.run();
            }
        }
    }

    private static final class RecordingHistory implements GuideHistoryAccess {
        private final List<GuideHistoryScope> loads = new ArrayList<>();
        private final CompletableFuture<Void> reset = new CompletableFuture<>();
        private final CompletableFuture<Void> secondLoad = new CompletableFuture<>();
        private CompletableFuture<Void> flushGate = CompletableFuture.completedFuture(null);
        private int resetCalls;

        @Override
        public CompletableFuture<java.util.Optional<GuideHistoryMetadata>> metadata(
                GuideHistoryScope scope) {
            loads.add(scope);
            if (loads.size() == 2) secondLoad.complete(null);
            return CompletableFuture.completedFuture(java.util.Optional.empty());
        }

        @Override
        public CompletableFuture<Void> commit(GuideHistoryCommit commit) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> delete(
                GuideHistoryDeleteScope scope) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletableFuture<Void> resetDatabase() {
            resetCalls++;
            return reset;
        }

        @Override
        public CompletableFuture<Void> flush() {
            return flushGate;
        }

        @Override
        public GuideHistoryActivity activity() {
            return new GuideHistoryActivity(0, resetCalls > 0 && !reset.isDone());
        }
    }

    private static final class IdleLocal implements GuideLocalEndpoint {
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override
        public CompletableFuture<dev.openallay.agent.AgentResult> ask(
                UUID actor, String sessionId, UUID requestId, String question,
                ToolInvocationContext context, Consumer<AgentEvent> events) {
            return new CompletableFuture<>();
        }
        @Override public boolean cancel(UUID actor, String sessionId) { return false; }
        @Override public void clearSession(UUID actor, String sessionId) {}
        @Override public void clearActor(UUID actor) {}
    }

    private static final class IdleRemote implements GuideRemoteEndpoint {
        @Override public boolean serverModelAvailable() { return false; }
        @Override public boolean serverToolsAvailable() { return false; }
        @Override
        public boolean ask(
                UUID requestId, String sessionId, String question, Consumer<AgentEvent> events) {
            return false;
        }
        @Override public boolean cancel(UUID requestId) { return false; }
        @Override public void disconnect() {}
    }
}
