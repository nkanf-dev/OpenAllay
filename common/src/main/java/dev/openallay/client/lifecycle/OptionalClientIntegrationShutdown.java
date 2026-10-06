package dev.openallay.client.lifecycle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Small shutdown custody seam. Normal bootstrap resolves no optional integration classes. */
public final class OptionalClientIntegrationShutdown {
    private static final Map<String, Runnable> callbacks = new LinkedHashMap<>();
    private static boolean stopping;
    private OptionalClientIntegrationShutdown() {}

    /** Returns an identity-bound retirement callback. Old owners cannot unregister replacements. */
    public static Runnable register(String owner, Runnable clear) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(clear, "clear");
        boolean late;
        synchronized (OptionalClientIntegrationShutdown.class) {
            late = stopping;
            if (!late) callbacks.put(owner, clear);
        }
        if (late) {
            clear.run(); // Shutdown won: retire the late runtime, never retain it.
            return () -> {};
        }
        return () -> {
            synchronized (OptionalClientIntegrationShutdown.class) { callbacks.remove(owner, clear); }
        };
    }
    public static void clear() {
        Map<String, Runnable> owned;
        synchronized (OptionalClientIntegrationShutdown.class) {
            stopping = true;
            owned = new LinkedHashMap<>(callbacks);
            callbacks.clear();
        }
        RuntimeException failure = null;
        for (Runnable callback : owned.values()) {
            try { callback.run(); }
            catch (Throwable error) {
                if (failure == null) failure = new IllegalStateException("Optional client integration shutdown failed", error);
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
