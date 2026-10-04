package dev.openallay.client.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Cancel hooks must be short and nonblocking (close/interrupt/destroy, never wait). */
public final class VoiceCancellation {
    private boolean cancelled;
    private final List<Runnable> hooks = new ArrayList<>();
    public synchronized boolean cancelled() { return cancelled; }
    public void check() { if (cancelled()) throw new CancellationException("Voice cancelled"); }
    public AutoCloseable onCancel(Runnable hook) {
        synchronized (this) {
            if (!cancelled) {
                hooks.add(hook);
                return () -> { synchronized (this) { hooks.remove(hook); } };
            }
        }
        hook.run();
        return () -> {};
    }
    public void cancel() { cancel(Runnable::run); }
    /** Set the fence now; run potentially blocking device cleanup on the supplied worker. */
    public void cancel(java.util.concurrent.Executor cleanup) {
        List<Runnable> callbacks;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true;
            callbacks = List.copyOf(hooks);
            hooks.clear();
        }
        if (!callbacks.isEmpty()) cleanup.execute(() -> {
            for (Runnable callback : callbacks) {
                try { callback.run(); } catch (RuntimeException ignored) { /* Reach all owners. */ }
            }
        });
    }
}
