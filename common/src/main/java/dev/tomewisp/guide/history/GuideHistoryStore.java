package dev.tomewisp.guide.history;

public interface GuideHistoryStore extends AutoCloseable {
    GuideHistoryLoad load(GuideHistoryScope scope);

    void save(GuideHistoryPartition partition);

    @Override
    default void close() {}
}
