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
    private static final class Deadlines {
        private static final java.util.concurrent.ScheduledThreadPoolExecutor TIMER = create();
        private static java.util.concurrent.ScheduledThreadPoolExecutor create() {
            java.util.concurrent.ScheduledThreadPoolExecutor executor = new java.util.concurrent.ScheduledThreadPoolExecutor(1, runnable -> {
                Thread thread = new Thread(runnable, "openallay-future-timeouts");
                thread.setDaemon(true); return thread;
            });
            executor.setRemoveOnCancelPolicy(true); return executor;
        }
    }
    /** Same standard future identity. A deadline cannot replace an already settled outcome. */
    public static <T> CompletableFuture<T> orTimeout(CompletableFuture<T> source, long timeout,
            java.util.concurrent.TimeUnit unit) {
        Objects.requireNonNull(source); Objects.requireNonNull(unit);
        if (!source.isDone()) {
            java.util.concurrent.ScheduledFuture<?> deadline = Deadlines.TIMER.schedule(
                    () -> source.completeExceptionally(new java.util.concurrent.TimeoutException()), timeout, unit);
            source.whenComplete((value, failure) -> deadline.cancel(false));
        }
        return source;
    }
}
