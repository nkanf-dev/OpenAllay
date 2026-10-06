package dev.openallay.server;

import java.util.concurrent.FutureTask;
import net.minecraft.server.MinecraftServer;

/** Genuine public 1.12 native queue; owner admission never executes the slice inline. */
final class NativeServerDeferredHandoff {
    private NativeServerDeferredHandoff() {}
    static void enqueue(MinecraftServer server, Runnable action) {
        if (!server.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Deferred slice admission requires native owner");
        }
        synchronized (server.futureTaskQueue) {
            server.futureTaskQueue.add(new FutureTask<Void>(action, null));
        }
    }
}
