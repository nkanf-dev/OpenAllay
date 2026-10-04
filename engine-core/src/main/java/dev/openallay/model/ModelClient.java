package dev.openallay.model;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ModelClient {
    /** True when each dispatched provider attempt already emits UsageObserved exactly once. */
    default boolean observesUsage() { return false; }

    /** True when AttemptStarted marks transport dispatch after local request preparation. */
    default boolean reportsAttemptStarted() { return false; }

    CompletableFuture<ModelTurn> complete(
            ModelRequest request,
            Consumer<ModelEvent> events,
            CancellationSignal cancellation);
}
