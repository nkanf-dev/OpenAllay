package dev.openallay.guide.history;

import dev.openallay.model.ModelMessage;
import java.util.List;
import java.util.UUID;

public interface GuideHistoryStore extends AutoCloseable {
    default java.util.Optional<GuideHistoryMetadata> metadata(GuideHistoryScope scope) {
        throw new UnsupportedOperationException("metadata reads are unavailable");
    }

    default GuideHistoryPage page(GuideHistoryPageRequest request) {
        throw new UnsupportedOperationException("page reads are unavailable");
    }

    default GuideHistoryContextSeed context(GuideHistoryContextRequest request) {
        throw new UnsupportedOperationException("context reads are unavailable");
    }

    /** Original request transcript; absent snapshots are empty, never reconstructed from display rows. */
    default List<ModelMessage> requestContext(
            GuideHistoryScope scope, UUID requestId) {
        throw new UnsupportedOperationException("request context reads are unavailable");
    }

    default void commit(GuideHistoryCommit commit) {
        throw new UnsupportedOperationException("incremental commits are unavailable");
    }

    /** Ordered atomic clone of full durable history through one completed request. */
    default GuideHistoryForkResult fork(GuideHistoryForkRequest request) {
        throw new UnsupportedOperationException("session forks are unavailable");
    }

    void delete(GuideHistoryDeleteScope scope);

    void resetDatabase();

    @Override
    default void close() {}
}
