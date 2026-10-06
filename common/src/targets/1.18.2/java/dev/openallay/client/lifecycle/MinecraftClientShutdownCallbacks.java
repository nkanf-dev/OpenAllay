package dev.openallay.client.lifecycle;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Typed shutdown handoff from native lifecycle registration to client close. */
public final class MinecraftClientShutdownCallbacks {
    private static final AtomicReference<Runnable> STOPPING = new AtomicReference<>();
    private MinecraftClientShutdownCallbacks() {}

    public static void install(Runnable stopping) {
        Objects.requireNonNull(stopping, "stopping");
        if (!STOPPING.compareAndSet(null, stopping)) {
            throw new IllegalStateException("Client shutdown callback is already installed");
        }
    }

    public static void onClose() {
        Runnable stopping = STOPPING.getAndSet(null);
        if (stopping != null) stopping.run();
    }
}
