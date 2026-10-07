package dev.openallay.util;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Java 8 future factories. */
public final class Java8Futures {
    private Java8Futures() {}

    public static <T> CompletableFuture<T> failedFuture(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(failure);
        return future;
    }
}
