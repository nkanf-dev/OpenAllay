package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.agent.context.ContextBudget;
import dev.openallay.model.ModelMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class GuideHistoryRepositoryTest {
    private static final GuideHistoryScope SCOPE = GuideHistoryScope.derive(
            UUID.fromString("cd4dac2b-f49a-460f-a6bb-ddc68c921ca4"),
            GuideHistoryScope.Kind.MULTIPLAYER,
            "repository.example");

    @Test
    void serializesWorkOffCallerAndFlushesLatestSubmission() throws Exception {
        BlockingStore store = new BlockingStore();
        GuideHistoryRepository repository = new GuideHistoryRepository(store);
        long caller = Thread.currentThread().threadId();

        var first = repository.commit(commit(1));
        assertTrue(store.entered.await(2, TimeUnit.SECONDS));
        var second = repository.commit(commit(2));
        var flush = repository.flush();
        assertFalse(second.isDone());
        assertFalse(flush.isDone());

        store.release.countDown();
        first.join();
        second.join();
        flush.join();

        assertEquals(List.of(1L, 2L), store.committedGenerations);
        assertTrue(store.threadNames.stream().allMatch(name -> name.startsWith("openallay-history-")));
        assertTrue(store.threadIds.stream().allMatch(id -> id != caller));
        repository.closeAsync().join();
    }

    @Test
    void preservesStructuredFailureAndContinuesLaterWork() {
        FailingStore store = new FailingStore();
        GuideHistoryRepository repository = new GuideHistoryRepository(store);

        CompletionException failure = assertThrows(
                CompletionException.class, () -> repository.commit(commit(1)).join());
        repository.commit(commit(2)).join();

        GuideHistoryException history = (GuideHistoryException) failure.getCause();
        assertEquals("history_write_failed", history.code());
        assertTrue(repository.activity().idleForDeletion());
        repository.delete(GuideHistoryDeleteScope.actor(SCOPE.actorId())).join();
        assertEquals(List.of(2L), store.committedGenerations);
        assertEquals(1, store.deleteCalls);
        repository.closeAsync().join();
    }

    @Test
    void deleteRejectsInsteadOfQueueingBehindPendingCommit() throws Exception {
        BlockingStore store = new BlockingStore();
        GuideHistoryRepository repository = new GuideHistoryRepository(store);
        CompletableFuture<Void> writing = repository.commit(commit(1));
        assertTrue(store.entered.await(2, TimeUnit.SECONDS));

        assertEquals(new GuideHistoryActivity(1, false), repository.activity());
        assertFutureCode(
                repository.delete(GuideHistoryDeleteScope.actor(SCOPE.actorId())),
                "history_delete_busy");
        assertFutureCode(repository.resetDatabase(), "history_delete_busy");
        assertEquals(0, store.deleteCalls);
        assertEquals(0, store.resetCalls);

        store.release.countDown();
        writing.join();
        assertTrue(repository.activity().idleForDeletion());
        repository.closeAsync().join();
    }

    @Test
    void reservedDeleteBlocksNewCommitAndCannotBeResurrected() throws Exception {
        DeletingStore store = new DeletingStore(false);
        GuideHistoryRepository repository = new GuideHistoryRepository(store);

        CompletableFuture<Void> deleting = repository.delete(
                GuideHistoryDeleteScope.partition(SCOPE));
        assertTrue(store.entered.await(2, TimeUnit.SECONDS));

        assertEquals(new GuideHistoryActivity(0, true), repository.activity());
        assertFutureCode(repository.metadata(SCOPE), "history_delete_busy");
        assertFutureCode(repository.commit(commit(1)), "history_delete_busy");
        assertFutureCode(
                repository.delete(GuideHistoryDeleteScope.actor(SCOPE.actorId())),
                "history_delete_busy");
        assertFutureCode(repository.resetDatabase(), "history_delete_busy");
        assertTrue(store.committedGenerations.isEmpty());

        store.release.countDown();
        deleting.join();
        assertTrue(repository.activity().idleForDeletion());
        repository.commit(commit(2)).join();
        assertEquals(List.of(2L), store.committedGenerations);
        repository.closeAsync().join();
    }

    @Test
    void resetUsesTheSameReservationAndCloseDrainsIt() throws Exception {
        DeletingStore store = new DeletingStore(true);
        GuideHistoryRepository repository = new GuideHistoryRepository(store);
        CompletableFuture<Void> resetting = repository.resetDatabase();
        assertTrue(store.entered.await(2, TimeUnit.SECONDS));

        CompletableFuture<Void> close = repository.closeAsync();
        assertFutureCode(repository.metadata(SCOPE), "history_repository_closed");
        assertFalse(close.isDone());

        store.release.countDown();
        resetting.join();
        close.join();
        assertEquals(1, store.resetCalls);
        assertTrue(store.closed);
    }

    @Test
    void cancellingReturnedCommitFutureDoesNotReleaseReservationOrFlushEarly() throws Exception {
        BlockingStore store = new BlockingStore();
        GuideHistoryRepository repository = new GuideHistoryRepository(store);
        CompletableFuture<Void> writing = repository.commit(commit(1));
        assertTrue(store.entered.await(2, TimeUnit.SECONDS));

        assertTrue(writing.cancel(false));
        assertEquals(new GuideHistoryActivity(1, false), repository.activity());
        assertFalse(repository.flush().isDone());
        assertFutureCode(
                repository.delete(GuideHistoryDeleteScope.actor(SCOPE.actorId())),
                "history_delete_busy");

        store.release.countDown();
        repository.flush().join();
        assertTrue(repository.activity().idleForDeletion());
        repository.closeAsync().join();
    }

    @Test
    void cancellingReturnedDeleteFutureDoesNotAllowAResurrectionCommit() throws Exception {
        DeletingStore store = new DeletingStore(false);
        GuideHistoryRepository repository = new GuideHistoryRepository(store);
        CompletableFuture<Void> deleting = repository.delete(
                GuideHistoryDeleteScope.partition(SCOPE));
        assertTrue(store.entered.await(2, TimeUnit.SECONDS));

        assertTrue(deleting.cancel(false));
        assertEquals(new GuideHistoryActivity(0, true), repository.activity());
        assertFalse(repository.flush().isDone());
        assertFutureCode(repository.commit(commit(1)), "history_delete_busy");

        store.release.countDown();
        repository.flush().join();
        assertTrue(repository.activity().idleForDeletion());
        assertTrue(store.committedGenerations.isEmpty());
        repository.closeAsync().join();
    }

    @Test
    void closeDrainsPriorWorkAndRejectsNewSubmission() throws Exception {
        BlockingStore store = new BlockingStore();
        GuideHistoryRepository repository = new GuideHistoryRepository(store);
        var writing = repository.commit(commit(1));
        assertTrue(store.entered.await(2, TimeUnit.SECONDS));

        var close = repository.closeAsync();
        CompletionException rejected = assertThrows(
                CompletionException.class, () -> repository.metadata(SCOPE).join());
        assertFalse(close.isDone());

        store.release.countDown();
        writing.join();
        close.join();

        assertTrue(store.closed);
        assertEquals("history_repository_closed",
                ((GuideHistoryException) rejected.getCause()).code());
    }

    @Test
    void incrementalCommitsAndWindowReadsShareOneOrderedWorker() throws Exception {
        WindowStore store = new WindowStore();
        GuideHistoryRepository repository = new GuideHistoryRepository(store);
        long caller = Thread.currentThread().threadId();
        GuideHistoryCommit commit = new GuideHistoryCommit(
                SCOPE, List.of(new GuideHistoryMutation.UpsertPartition(
                        "main", Instant.EPOCH)));

        CompletableFuture<Void> writing = repository.commit(commit);
        assertTrue(store.entered.await(2, TimeUnit.SECONDS));
        CompletableFuture<Optional<GuideHistoryMetadata>> metadata =
                repository.metadata(SCOPE);
        CompletableFuture<GuideHistoryPage> page = repository.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.NEWEST, null, 1));
        CompletableFuture<GuideHistoryContextSeed> context = repository.context(
                new GuideHistoryContextRequest(
                        SCOPE, "main", new ContextBudget(8_192, 1_024), 256, "fixture-model"));
        UUID requestId = UUID.fromString("1911776f-4b93-4107-82e3-7d901d0346b8");
        CompletableFuture<List<ModelMessage>> original = repository.requestContext(SCOPE, requestId);
        assertFalse(metadata.isDone());
        assertFalse(page.isDone());
        assertFalse(context.isDone());
        assertFalse(original.isDone());
        assertEquals(new GuideHistoryActivity(1, false), repository.activity());

        store.release.countDown();
        writing.join();
        assertTrue(metadata.join().isEmpty());
        assertTrue(page.join().requests().isEmpty());
        assertEquals(List.of(ModelMessage.userText("active worker context")), context.join().messages());
        assertEquals(List.of(ModelMessage.userText("original worker request")), original.join());
        assertEquals(List.of("commit", "metadata", "page", "context", "requestContext"),
                store.operations);
        assertTrue(store.threadIds.stream().allMatch(id -> id != caller));
        repository.closeAsync().join();
    }

    private static GuideHistoryCommit commit(long generation) {
        return new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.UpsertPartition(
                "main", Instant.ofEpochMilli(generation))));
    }

    private static long generation(GuideHistoryCommit commit) {
        return commit.mutations().stream()
                .filter(GuideHistoryMutation.UpsertPartition.class::isInstance)
                .map(GuideHistoryMutation.UpsertPartition.class::cast)
                .findFirst().orElseThrow().updatedAt().toEpochMilli();
    }

    private static void assertFutureCode(CompletableFuture<?> future, String code) {
        CompletionException failure = assertThrows(CompletionException.class, future::join);
        assertEquals(code, ((GuideHistoryException) failure.getCause()).code());
    }

    private static final class BlockingStore implements GuideHistoryStore {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Long> committedGenerations = new ArrayList<>();
        private final List<Long> threadIds = new ArrayList<>();
        private final List<String> threadNames = new ArrayList<>();
        private int deleteCalls;
        private int resetCalls;
        private volatile boolean closed;

        @Override
        public Optional<GuideHistoryMetadata> metadata(GuideHistoryScope scope) {
            recordThread();
            return Optional.empty();
        }

        @Override
        public void commit(GuideHistoryCommit commit) {
            recordThread();
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new GuideHistoryException(
                        "history_write_failed", "History worker was interrupted", interrupted);
            }
            committedGenerations.add(generation(commit));
        }

        @Override
        public void delete(GuideHistoryDeleteScope scope) {
            deleteCalls++;
        }

        @Override
        public void resetDatabase() {
            resetCalls++;
        }

        @Override
        public void close() {
            recordThread();
            closed = true;
        }

        private void recordThread() {
            threadIds.add(Thread.currentThread().threadId());
            threadNames.add(Thread.currentThread().getName());
        }
    }

    private static final class FailingStore implements GuideHistoryStore {
        private final List<Long> committedGenerations = new ArrayList<>();
        private boolean first = true;
        private int deleteCalls;

        @Override
        public Optional<GuideHistoryMetadata> metadata(GuideHistoryScope scope) {
            return Optional.empty();
        }

        @Override
        public void commit(GuideHistoryCommit commit) {
            if (first) {
                first = false;
                throw new GuideHistoryException(
                        "history_write_failed", "injected history failure");
            }
            committedGenerations.add(generation(commit));
        }

        @Override
        public void delete(GuideHistoryDeleteScope scope) {
            deleteCalls++;
        }

        @Override
        public void resetDatabase() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class DeletingStore implements GuideHistoryStore {
        private final boolean reset;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Long> committedGenerations = new ArrayList<>();
        private int deleteCalls;
        private int resetCalls;
        private volatile boolean closed;

        private DeletingStore(boolean reset) {
            this.reset = reset;
        }

        @Override
        public Optional<GuideHistoryMetadata> metadata(GuideHistoryScope scope) {
            return Optional.empty();
        }

        @Override
        public void commit(GuideHistoryCommit commit) {
            committedGenerations.add(generation(commit));
        }

        @Override
        public void delete(GuideHistoryDeleteScope scope) {
            if (reset) {
                throw new AssertionError("delete was called instead of reset");
            }
            deleteCalls++;
            block();
        }

        @Override
        public void resetDatabase() {
            if (!reset) {
                throw new AssertionError("reset was called instead of delete");
            }
            resetCalls++;
            block();
        }

        @Override
        public void close() {
            closed = true;
        }

        private void block() {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new GuideHistoryException(
                        "history_delete_failed", "History worker was interrupted", interrupted);
            }
        }
    }

    private static final class WindowStore implements GuideHistoryStore {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<String> operations = new ArrayList<>();
        private final List<Long> threadIds = new ArrayList<>();

        @Override
        public void commit(GuideHistoryCommit commit) {
            operations.add("commit");
            threadIds.add(Thread.currentThread().threadId());
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new GuideHistoryException(
                        "history_write_failed", "interrupted", interrupted);
            }
        }

        @Override
        public Optional<GuideHistoryMetadata> metadata(GuideHistoryScope scope) {
            operations.add("metadata");
            threadIds.add(Thread.currentThread().threadId());
            return Optional.empty();
        }

        @Override
        public GuideHistoryPage page(GuideHistoryPageRequest request) {
            assertEquals(SCOPE, request.scope());
            operations.add("page");
            threadIds.add(Thread.currentThread().threadId());
            return new GuideHistoryPage(request.sessionId(), List.of(), null, null, false, false);
        }

        @Override
        public GuideHistoryContextSeed context(GuideHistoryContextRequest request) {
            assertEquals(SCOPE, request.scope());
            operations.add("context");
            threadIds.add(Thread.currentThread().threadId());
            return new GuideHistoryContextSeed(request.sessionId(),
                    List.of(ModelMessage.userText("active worker context")), List.of(), 10);
        }

        @Override
        public List<ModelMessage> requestContext(GuideHistoryScope scope, UUID requestId) {
            assertEquals(SCOPE, scope);
            assertEquals(UUID.fromString("1911776f-4b93-4107-82e3-7d901d0346b8"), requestId);
            operations.add("requestContext");
            threadIds.add(Thread.currentThread().threadId());
            return List.of(ModelMessage.userText("original worker request"));
        }

        @Override public void delete(GuideHistoryDeleteScope scope) {}
        @Override public void resetDatabase() {}
    }
}
