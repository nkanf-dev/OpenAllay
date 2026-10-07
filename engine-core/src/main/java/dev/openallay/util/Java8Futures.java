package dev.openallay.util;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Java 8 future operations. */
public final class Java8Futures {
    private Java8Futures() {}

    public static <T> CompletableFuture<T> failedFuture(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(failure);
        return future;
    }
    /** Normal completion stays inline; only exceptional recovery uses the default async executor. */
    public static <T> CompletableFuture<T> exceptionallyAsync(
            CompletableFuture<T> source, java.util.function.Function<Throwable, ? extends T> recovery) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(recovery, "recovery");
        return source.<CompletableFuture<T>>handle((value, failure) -> {
            if (failure == null) return CompletableFuture.completedFuture(value);
            return CompletableFuture.<T>supplyAsync(() -> recovery.apply(failure));
        }).thenCompose(java.util.function.Function.identity());
    }
}
