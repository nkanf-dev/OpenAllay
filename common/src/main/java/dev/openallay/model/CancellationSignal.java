package dev.openallay.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationSignal implements dev.openallay.net.HttpCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final List<Runnable> listeners = new ArrayList<>();

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new ModelClientException(
                    new ModelFailure("agent_cancelled", "Agent request was cancelled", null));
        }
    }

    public void onCancel(Runnable listener) {
        java.util.Objects.requireNonNull(listener, "listener");
        boolean runNow;
        synchronized (listeners) {
            runNow = cancelled.get();
            if (!runNow) {
                listeners.add(listener);
            }
        }
        if (runNow) notifyListener(listener);
    }

    public boolean cancel() {
        return cancel(Runnable::run);
    }

    /** Revokes admission immediately; notifications can run off the caller's owner thread. */
    public boolean cancel(java.util.concurrent.Executor notifications) {
        java.util.Objects.requireNonNull(notifications, "notifications");
        if (!cancelled.compareAndSet(false, true)) return false;
        List<Runnable> snapshot;
        synchronized (listeners) {
            snapshot = List.copyOf(listeners);
            listeners.clear();
        }
        AtomicBoolean notified = new AtomicBoolean();
        Runnable notifyAll = () -> {
            if (notified.compareAndSet(false, true)) snapshot.forEach(CancellationSignal::notifyListener);
        };
        try {
            notifications.execute(notifyAll);
        } catch (Throwable rejected) {
            // Revocation is already final. A rejected notification executor cannot drop cleanup.
            notifyAll.run();
        }
        return true;
    }

    private static void notifyListener(Runnable listener) {
        try { listener.run(); }
        catch (Throwable ignored) {
            // Cancellation is best-effort notification, not a foreign exception boundary.
            // Even Error/ThreadDeath from a callback cannot drop another listener's cleanup.
        }
    }

    /** Settles the observed operation even when its raw provider future ignores cancellation. */
    public <T> java.util.concurrent.CompletableFuture<T> observe(
            java.util.concurrent.CompletableFuture<T> raw) {
        java.util.concurrent.CompletableFuture<T> observed = new java.util.concurrent.CompletableFuture<>();
        raw.whenComplete((value, failure) -> {
            if (failure == null) observed.complete(value);
            else observed.completeExceptionally(failure);
        });
        onCancel(() -> observed.completeExceptionally(new ModelClientException(
                new ModelFailure("agent_cancelled", "Agent request was cancelled", null))));
        return observed;
    }

}
