package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideServiceManagerReadinessTest {
    @Test void memoryOnlySameActorNewWorldWaitsForOldActualCleanupBeforeCapturingOrStarting() throws Exception {
        for (boolean failedCleanup : List.of(false, true)) {
        Owner owner = new Owner(); Endpoint endpoint = new Endpoint(); Contexts contexts = new Contexts(failedCleanup);
        UUID actor = UUID.randomUUID(); GuideServiceManager manager = new GuideServiceManager(endpoint,
                new GuideRemoteEndpoint() {
                    public boolean serverModelAvailable() { return false; }
                    public boolean serverToolsAvailable() { return false; }
                    public boolean ask(UUID id, String session, String question, Consumer<AgentEvent> events) { return false; }
                    public boolean cancel(UUID id) { return true; }
                    public void disconnect() {}
                }, contexts, owner::execute, Clock.systemUTC(), dev.openallay.json.EngineJson.create());
        GuideService firstService = manager.forActor(actor);
        var askingFirst = firstService.ask("old world task"); owner.runAll(); UUID first = success(askingFirst.join());
        assertEquals(1, contexts.captures); assertEquals(List.of(first), endpoint.started);
        // A physical world/connection boundary can replace a memory-only service with the same actor.
        CompletableFuture<Void> detached = manager.disconnect(); owner.runAll();
        GuideService nextService = manager.forActor(actor);
        var askingNext = nextService.ask("new world task");
        var followUp = nextService.followUp("next input in arrival order"); owner.runAll();
        assertFalse(detached.isDone()); assertFalse(askingNext.isDone()); assertFalse(followUp.isDone());
        assertEquals(1, contexts.captures); assertEquals(List.of(first), endpoint.started);
        // Real owner-thread observer handoff precedes the real endpoint's completion.
        List<ModelMessage> actual = List.of(ModelMessage.userText("old world task"), ModelMessage.userText("actual final context"));
        owner.execute(() -> endpoint.events.get(first).accept(new AgentEvent.ContextFinalized(actual, actual)));
        owner.execute(() -> endpoint.futures.get(first).complete(new AgentResult(
                AgentState.CANCELLED, null, "agent_cancelled", "cancelled", null)));
        owner.until(askingNext::isDone);
        UUID next = success(askingNext.join()); success(followUp.join());
        if (failedCleanup) assertThrows(java.util.concurrent.CompletionException.class, detached::join);
        else detached.join();
        assertEquals(List.of("next input in arrival order"), nextService.pendingMessages("main").stream()
                .map(GuidePendingMessage::text).toList(), "barrier completion must not reverse ask and follow-up");
        assertEquals(1, endpoint.actorClears);
        assertEquals(2, contexts.captures);
        assertEquals(List.of(first, next), endpoint.started);
        assertEquals(Set.of(next), endpoint.active,
                "old clearActor must finish before admission, never erase the replacement's actual task");
        assertFalse(endpoint.futures.get(next).isDone());
        assertEquals(next, nextService.snapshot().sessions().getFirst().workingRequestId());
        assertSame(nextService, manager.current());
        }
    }

    private static <T> T success(ToolResult<T> result) {
        return ((ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result)).value();
    }
    private static final class Contexts implements GuideContextProvider {
        int captures;
        final boolean failedCleanup;
        Contexts(boolean failedCleanup) { this.failedCleanup = failedCleanup; }
        public CompletableFuture<Void> releaseObservationImages(String correlation) {
            return failedCleanup ? CompletableFuture.failedFuture(new java.io.IOException("real cleanup failure"))
                    : CompletableFuture.completedFuture(null);
        }
        public ToolResult<ToolInvocationContext> capture(Set<ContextCapability> required, String correlation) {
            captures++; return new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation));
        }
        public CompletableFuture<Void> detachConnectionState(CompletableFuture<Void> custody) { return custody; }
    }
    private static final class Endpoint implements GuideLocalEndpoint {
        int actorClears;
        final List<UUID> started = new ArrayList<>(); final Set<UUID> active = ConcurrentHashMap.newKeySet();
        final Map<UUID, Consumer<AgentEvent>> events = new ConcurrentHashMap<>();
        final Map<UUID, CompletableFuture<AgentResult>> futures = new ConcurrentHashMap<>();
        public Set<ContextCapability> requiredContext() { return Set.of(); }
        public CompletableFuture<AgentResult> ask(UUID actor, String session, UUID id, String question,
                ToolInvocationContext context, Consumer<AgentEvent> events) {
            started.add(id); active.add(id); this.events.put(id, events);
            var future = new CompletableFuture<AgentResult>(); futures.put(id, future); return future;
        }
        public boolean cancel(UUID actor, String session) { return true; }
        public void clearSession(UUID actor, String session) {}
        public void clearActor(UUID actor) {
            actorClears++; active.clear();
            futures.values().forEach(future -> future.completeExceptionally(new IllegalStateException("actor cleared")));
        }
    }
    private static final class Owner {
        final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>(); final Semaphore ready = new Semaphore(0);
        void execute(Runnable task) { tasks.add(task); ready.release(); }
        void runAll() { while (ready.tryAcquire()) tasks.remove().run(); }
        void until(BooleanSupplier condition) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!condition.getAsBoolean()) {
                long remaining = deadline - System.nanoTime();
                assertTrue(remaining > 0 && ready.tryAcquire(remaining, TimeUnit.NANOSECONDS), "real cleanup must settle");
                tasks.remove().run(); runAll();
            }
        }
    }
}
