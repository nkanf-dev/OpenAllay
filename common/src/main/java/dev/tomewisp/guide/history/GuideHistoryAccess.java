package dev.tomewisp.guide.history;

import java.util.concurrent.CompletableFuture;

public interface GuideHistoryAccess {
    CompletableFuture<GuideHistoryLoad> load(GuideHistoryScope scope);

    CompletableFuture<Void> save(GuideHistoryPartition partition);

    CompletableFuture<Void> flush();
}
