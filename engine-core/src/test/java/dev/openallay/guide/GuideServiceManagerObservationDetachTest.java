package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class GuideServiceManagerObservationDetachTest {
    @Test void replacementDetachesBeforeNewCapturesAndNeverRunsGlobalClearOnOldCompletion() throws Exception {
        for (boolean failure : List.of(false, true)) {
            Owner owner = new Owner(); Contexts contexts = new Contexts(); History history = new History();
            GuideServiceManager manager = new GuideServiceManager(null, new GuideRemoteEndpoint() {
                public boolean serverModelAvailable() { return false; }
                public boolean serverToolsAvailable() { return false; }
                public boolean ask(UUID id, String session, String question, java.util.function.Consumer<dev.openallay.agent.AgentEvent> events) { return false; }
                public boolean cancel(UUID id) { return false; }
                public void disconnect() {}
            }, contexts, owner::execute, Clock.systemUTC(), new Gson(), history,
                    actor -> GuideHistoryScope.derive(actor, GuideHistoryScope.Kind.MULTIPLAYER, "fixture.example"));
            manager.forActor(UUID.randomUUID()); run(owner);
            contexts.capture(Set.of(), "old");
            GuideService replacement = manager.forActor(UUID.randomUUID());
            assertEquals(1, contexts.clears, "only initial no-owner boundary may globally clear");
            assertEquals(List.of("old"), contexts.detached);
            contexts.capture(Set.of(), "new"); run(owner);
            assertFalse(contexts.custody.isDone());
            if (failure) history.flush.completeExceptionally(new IllegalStateException("injected flush failure"));
            else history.flush.complete(null);
            owner.until(contexts.custody::isDone);
            if (failure) assertThrows(java.util.concurrent.CompletionException.class, contexts.custody::join);
            else contexts.custody.join();
            run(owner);
            assertEquals(List.of("new"), contexts.active);
            assertEquals(1, contexts.clears);
            assertSame(replacement, manager.current());
        }
    }
    private static void run(Owner owner) { owner.runAll(); }
    private static final class Owner {
        final java.util.concurrent.ConcurrentLinkedQueue<Runnable> tasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
        final java.util.concurrent.Semaphore ready = new java.util.concurrent.Semaphore(0);
        void execute(Runnable task) { tasks.add(task); ready.release(); }
        void runAll() { while (ready.tryAcquire()) tasks.remove().run(); }
        void until(java.util.function.BooleanSupplier condition) throws Exception {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (!condition.getAsBoolean()) {
                long remaining = deadline - System.nanoTime();
                assertTrue(remaining > 0 && ready.tryAcquire(remaining, java.util.concurrent.TimeUnit.NANOSECONDS),
                        "actual owner-thread cleanup must arrive");
                tasks.remove().run(); runAll();
            }
        }
    }
    private static final class Contexts implements GuideContextProvider {
        int clears; final List<String> active = new ArrayList<>(); List<String> detached = List.of();
        CompletableFuture<Void> custody;
        public ToolResult<ToolInvocationContext> capture(Set<ContextCapability> capabilities, String id) {
            active.add(id); return new ToolResult.Success<>(ToolInvocationContext.developmentConsole(id));
        }
        public void clearConnectionState() { clears++; active.clear(); }
        public CompletableFuture<Void> detachConnectionState(CompletableFuture<Void> custody) {
            detached = List.copyOf(active); active.clear(); this.custody = custody;
            return custody;
        }
    }
    private static final class History implements GuideHistoryAccess {
        final CompletableFuture<Void> flush = new CompletableFuture<>();
        public CompletableFuture<Optional<GuideHistoryMetadata>> metadata(GuideHistoryScope scope) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        public CompletableFuture<dev.openallay.guide.history.GuideHistoryPage> page(dev.openallay.guide.history.GuideHistoryPageRequest request) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        public CompletableFuture<dev.openallay.guide.history.GuideHistoryContextSeed> context(dev.openallay.guide.history.GuideHistoryContextRequest request) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        public CompletableFuture<Void> commit(GuideHistoryCommit commit) {
            return CompletableFuture.completedFuture(null);
        }
        public CompletableFuture<Void> resetDatabase() { return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Void> delete(GuideHistoryDeleteScope scope) { return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Void> flush() { return flush; }
        public GuideHistoryActivity activity() { return GuideHistoryActivity.idle(); }
    }
}
