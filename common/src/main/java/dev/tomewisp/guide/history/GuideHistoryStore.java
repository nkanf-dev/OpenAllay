package dev.tomewisp.guide.history;

public interface GuideHistoryStore extends AutoCloseable {
    GuideHistoryLoad load(GuideHistoryScope scope);

    void save(GuideHistoryPartition partition);

    void delete(GuideHistoryDeleteScope scope);

    void resetDatabase();

    @Override
    default void close() {}
}
