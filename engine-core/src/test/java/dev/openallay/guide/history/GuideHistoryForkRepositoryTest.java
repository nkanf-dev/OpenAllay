package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.guide.GuideModelSelection;
import dev.openallay.model.ModelMessage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class GuideHistoryForkRepositoryTest {
    private static final GuideHistoryScope SCOPE = GuideHistoryScope.derive(
            UUID.fromString("06e5413b-e2a9-4e8e-b452-b53507255cdb"),
            GuideHistoryScope.Kind.MULTIPLAYER, "repository-fork.example");

    @Test
    void forkRunsOnOrderedWorkerAndCancelledOutwardFutureDoesNotReleaseWriteReservation() throws Exception {
        Store store = new Store(); GuideHistoryRepository repository = new GuideHistoryRepository(store);
        long caller = Thread.currentThread().threadId();
        CompletableFuture<GuideHistoryForkResult> fork = repository.fork(request());
        try {
            assertTrue(store.entered.await(2, TimeUnit.SECONDS));
            assertNotEquals(caller, store.threadId);
            assertEquals(new GuideHistoryActivity(1, false), repository.activity());
            assertTrue(fork.cancel(false));
            assertFalse(repository.flush().isDone());
            CompletionException busy = assertThrows(CompletionException.class, () -> repository.delete(
                    GuideHistoryDeleteScope.partition(SCOPE)).join());
            assertEquals("history_delete_busy", ((GuideHistoryException) busy.getCause()).code());
            CompletableFuture<Void> close = repository.closeAsync();
            assertFalse(close.isDone());
            store.release.countDown();
            close.join();
            assertTrue(store.closed);
            assertTrue(repository.activity().idleForDeletion());
        } finally {
            store.release.countDown(); repository.closeAsync().join();
        }
    }

    @Test
    void forkFailureRetainsTypedCodeAndReleasesReservation() {
        Store store = new Store(); store.fail = true; store.release.countDown();
        GuideHistoryRepository repository = new GuideHistoryRepository(store);
        try {
            CompletionException failure = assertThrows(CompletionException.class,
                    () -> repository.fork(request()).join());
            assertEquals("fork_boundary_unavailable", ((GuideHistoryException) failure.getCause()).code());
            assertTrue(repository.activity().idleForDeletion());
            repository.delete(GuideHistoryDeleteScope.partition(SCOPE)).join();
        } finally { repository.closeAsync().join(); }
    }

    private static GuideHistoryForkRequest request() {
        return new GuideHistoryForkRequest(SCOPE, new GuideHistoryMutation.ForkSession("main",
                new GuideHistoryCursor(0, UUID.randomUUID()), "branch", 1, GuideModelSelection.client("profile")));
    }
    private static final class Store implements GuideHistoryStore {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private long threadId;
        private boolean fail;
        private boolean closed;
        @Override public GuideHistoryForkResult fork(GuideHistoryForkRequest request) {
            threadId = Thread.currentThread().threadId(); entered.countDown();
            try { release.await(); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted);
            }
            if (fail) throw new GuideHistoryException("fork_boundary_unavailable", "synthetic unavailable boundary");
            UUID requestId = UUID.randomUUID();
            GuideHistoryCursor cursor = new GuideHistoryCursor(0, requestId);
            java.time.Instant now = java.time.Instant.EPOCH;
            dev.openallay.guide.GuideRequestSnapshot inherited = new dev.openallay.guide.GuideRequestSnapshot(
                    requestId, "branch", dev.openallay.guide.GuideTopology.CLIENT_LOCAL,
                    "synthetic completed", List.of(), dev.openallay.guide.GuideRequestStatus.COMPLETED,
                    List.of(), dev.openallay.model.ModelUsage.empty(), null, null, now, now, now,
                    GuideModelSelection.client("profile"));
            return new GuideHistoryForkResult(new GuideHistoryMetadata.Session("branch", 1,
                    GuideModelSelection.client("profile"), 1, cursor, cursor),
                    new GuideHistoryPage("branch", List.of(inherited), cursor, cursor, false, false),
                    List.of(ModelMessage.userText("actual context")), List.of());
        }
        @Override public void delete(GuideHistoryDeleteScope scope) {}
        @Override public void resetDatabase() {}
        @Override public void close() { closed = true; }
    }
}
