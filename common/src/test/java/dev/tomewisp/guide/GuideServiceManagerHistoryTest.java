package dev.tomewisp.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import dev.tomewisp.agent.AgentEvent;
import dev.tomewisp.context.ContextCapability;
import dev.tomewisp.context.ToolInvocationContext;
import dev.tomewisp.guide.history.GuideHistoryAccess;
import dev.tomewisp.guide.history.GuideHistoryLoad;
import dev.tomewisp.guide.history.GuideHistoryPartition;
import dev.tomewisp.guide.history.GuideHistoryScope;
import dev.tomewisp.tool.ToolResult;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideServiceManagerHistoryTest {
    private static final UUID ACTOR =
            UUID.fromString("77a70151-0279-4ee6-a6b9-b15c24d08f05");

    @Test
    void waitsForPreviousDisconnectBeforeLoadingReplacementScope() {
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
        selected[0] = second;

        GuideService replacement = manager.forActor(ACTOR);

        assertEquals(GuidePersistenceSnapshot.State.LOADING,
                replacement.snapshot().persistence().state());
        assertEquals(List.of(first), history.loads);
        dispatcher.runAll();
        assertEquals(List.of(first, second), history.loads);
    }

    private static final class QueuedDispatcher implements dev.tomewisp.client.ClientEventDispatcher {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

        @Override
        public void execute(Runnable event) {
            queued.add(event);
        }

        private void runAll() {
            while (!queued.isEmpty()) {
                queued.remove().run();
            }
        }
    }

    private static final class RecordingHistory implements GuideHistoryAccess {
        private final List<GuideHistoryScope> loads = new ArrayList<>();

        @Override
        public CompletableFuture<GuideHistoryLoad> load(GuideHistoryScope scope) {
            loads.add(scope);
            return CompletableFuture.completedFuture(GuideHistoryLoad.empty());
        }

        @Override
        public CompletableFuture<Void> save(GuideHistoryPartition partition) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> flush() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class IdleLocal implements GuideLocalEndpoint {
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override
        public CompletableFuture<dev.tomewisp.agent.AgentResult> ask(
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
