package dev.openallay.guide.history;

import dev.openallay.model.ModelMessage;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Ordered asynchronous boundary around the synchronous durable store. */
public final class GuideHistoryRepository implements GuideHistoryAccess {
    private final GuideHistoryStore store;
    private final ExecutorService worker;
    private CompletableFuture<Void> latest = CompletableFuture.completedFuture(null);
    private CompletableFuture<Void> closeFuture;
    private int pendingWrites;
    private boolean deleting;
    private boolean closing;

    public GuideHistoryRepository(GuideHistoryStore store) {
        this(
                store,
                Executors.newSingleThreadExecutor(Thread.ofPlatform()
                        .name("openallay-history-", 0)
                        .daemon(true)
                        .factory()));
    }

    GuideHistoryRepository(GuideHistoryStore store, ExecutorService worker) {
        this.store = Objects.requireNonNull(store, "store");
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    @Override
    public synchronized CompletableFuture<java.util.Optional<GuideHistoryMetadata>> metadata(
            GuideHistoryScope scope) {
        Objects.requireNonNull(scope, "scope");
        return submitReadLocked(
                "history_metadata_failed",
                "Unable to load guide history metadata",
                () -> store.metadata(scope));
    }

    @Override
    public synchronized CompletableFuture<GuideHistoryPage> page(
            GuideHistoryPageRequest request) {
        Objects.requireNonNull(request, "request");
        return submitReadLocked(
                "history_page_failed",
                "Unable to load a guide history page",
                () -> store.page(request));
    }

    @Override
    public synchronized CompletableFuture<GuideHistoryContextSeed> context(
            GuideHistoryContextRequest request) {
        Objects.requireNonNull(request, "request");
        return submitReadLocked(
                "history_context_failed",
                "Unable to prepare guide history context",
                () -> store.context(request));
    }

    @Override
    public synchronized CompletableFuture<List<ModelMessage>> requestContext(
            GuideHistoryScope scope, UUID requestId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(requestId, "requestId");
        return submitReadLocked(
                "history_context_failed",
                "Unable to load original guide request context",
                () -> store.requestContext(scope, requestId));
    }

    @Override
    public synchronized CompletableFuture<Void> commit(GuideHistoryCommit commit) {
        Objects.requireNonNull(commit, "commit");
        return reserveWrite(() -> store.commit(commit));
    }

    @Override
    public synchronized CompletableFuture<GuideHistoryForkResult> fork(GuideHistoryForkRequest request) {
        Objects.requireNonNull(request, "request");
        return reserveMutation(() -> store.fork(request));
    }

    private CompletableFuture<Void> reserveWrite(Runnable operation) {
        return reserveMutation(() -> {
            operation.run();
            return null;
        });
    }

    private <T> CompletableFuture<T> reserveMutation(Supplier<T> operation) {
        if (closing) {
            return closedFailure();
        }
        if (deleting) {
            return busyFailure();
        }
        pendingWrites++;
        CompletableFuture<T> scheduled;
        try {
            scheduled = CompletableFuture.supplyAsync(() -> guarded(
                    "history_write_failed",
                    "Unable to save durable guide history",
                    operation), worker);
        } catch (RuntimeException failure) {
            pendingWrites--;
            throw failure;
        }
        ReservationFutures<T> tracked = trackReservation(scheduled, this::completeWrite);
        latest = tracked.internal().handle((ignored, failure) -> null);
        return tracked.outward();
    }

    @Override
    public synchronized CompletableFuture<Void> delete(GuideHistoryDeleteScope scope) {
        Objects.requireNonNull(scope, "scope");
        return reserveDeletion(() -> store.delete(scope));
    }

    @Override
    public synchronized CompletableFuture<Void> resetDatabase() {
        return reserveDeletion(store::resetDatabase);
    }

    @Override
    public synchronized GuideHistoryActivity activity() {
        return new GuideHistoryActivity(pendingWrites, deleting);
    }

    private CompletableFuture<Void> reserveDeletion(Runnable operation) {
        if (closing) {
            return closedFailure();
        }
        if (pendingWrites != 0 || deleting) {
            return busyFailure();
        }
        deleting = true;
        CompletableFuture<Void> scheduled;
        try {
            scheduled = CompletableFuture.supplyAsync(() -> guarded(
                    "history_delete_failed",
                    "Unable to delete durable guide history",
                    () -> {
                        operation.run();
                        return null;
                    }), worker);
        } catch (RuntimeException failure) {
            deleting = false;
            throw failure;
        }
        ReservationFutures<Void> tracked = trackReservation(scheduled, this::completeDeletion);
        latest = tracked.internal().handle((ignored, failure) -> null);
        return tracked.outward();
    }

    private synchronized void completeWrite() {
        pendingWrites--;
    }

    private synchronized void completeDeletion() {
        deleting = false;
    }

    private static <T> ReservationFutures<T> trackReservation(
            CompletableFuture<T> scheduled, Runnable release) {
        CompletableFuture<T> internal = new CompletableFuture<>();
        CompletableFuture<T> outward = new CompletableFuture<>();
        scheduled.whenComplete((value, failure) -> {
            release.run();
            if (failure == null) {
                internal.complete(value);
                outward.complete(value);
            } else {
                internal.completeExceptionally(failure);
                outward.completeExceptionally(failure);
            }
        });
        return new ReservationFutures<>(internal, outward);
    }

    private record ReservationFutures<T>(
            CompletableFuture<T> internal,
            CompletableFuture<T> outward) {}

    private static <T> CompletableFuture<T> busyFailure() {
        return CompletableFuture.failedFuture(new GuideHistoryException(
                "history_delete_busy", "Guide history is busy"));
    }

    private static <T> CompletableFuture<T> closedFailure() {
        return CompletableFuture.failedFuture(new GuideHistoryException(
                "history_repository_closed", "Guide history repository is closed"));
    }

    private synchronized <T> CompletableFuture<T> submitLocked(
            String failureCode,
            String failureMessage,
            Supplier<T> operation) {
        if (closing) {
            return closedFailure();
        }
        CompletableFuture<T> future = CompletableFuture.supplyAsync(
                () -> guarded(failureCode, failureMessage, operation), worker);
        latest = future.handle((ignored, failure) -> null);
        return future;
    }

    private <T> CompletableFuture<T> submitReadLocked(
            String failureCode,
            String failureMessage,
            Supplier<T> operation) {
        if (closing) {
            return closedFailure();
        }
        if (deleting) {
            return busyFailure();
        }
        return submitLocked(failureCode, failureMessage, operation);
    }

    @Override
    public synchronized CompletableFuture<Void> flush() {
        return latest;
    }

    public synchronized CompletableFuture<Void> closeAsync() {
        if (closeFuture != null) {
            return closeFuture;
        }
        closing = true;
        CompletableFuture<Void> closingTask = CompletableFuture.runAsync(store::close, worker);
        latest = closingTask.handle((ignored, failure) -> null);
        closeFuture = closingTask.whenComplete((ignored, failure) -> worker.shutdown());
        return closeFuture;
    }

    private static <T> T guarded(
            String failureCode,
            String failureMessage,
            Supplier<T> operation) {
        try {
            return operation.get();
        } catch (GuideHistoryException known) {
            throw known;
        } catch (RuntimeException unexpected) {
            throw new GuideHistoryException(failureCode, failureMessage, unexpected);
        }
    }
}
