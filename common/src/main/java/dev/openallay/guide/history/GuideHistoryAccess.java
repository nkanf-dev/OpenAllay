package dev.openallay.guide.history;

import dev.openallay.model.ModelMessage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuideHistoryAccess {
    default CompletableFuture<java.util.Optional<GuideHistoryMetadata>> metadata(
            GuideHistoryScope scope) {
        return unsupported();
    }

    default CompletableFuture<GuideHistoryPage> page(GuideHistoryPageRequest request) {
        return unsupported();
    }

    default CompletableFuture<GuideHistoryContextSeed> context(GuideHistoryContextRequest request) {
        return unsupported();
    }

    /** Original request transcript; absent snapshots are empty, never reconstructed from display rows. */
    default CompletableFuture<List<ModelMessage>> requestContext(
            GuideHistoryScope scope, UUID requestId) {
        return unsupported();
    }

    default CompletableFuture<Void> commit(GuideHistoryCommit commit) {
        return unsupported();
    }

    /** Ordered atomic clone of full durable history through one completed request. */
    default CompletableFuture<GuideHistoryForkResult> fork(GuideHistoryForkRequest request) {
        return unsupported();
    }

    CompletableFuture<Void> delete(GuideHistoryDeleteScope scope);

    CompletableFuture<Void> resetDatabase();

    CompletableFuture<Void> flush();

    GuideHistoryActivity activity();

    private static <T> CompletableFuture<T> unsupported() {
        return CompletableFuture.failedFuture(new GuideHistoryException(
                "history_operation_unsupported", "History operation is unavailable"));
    }
}
