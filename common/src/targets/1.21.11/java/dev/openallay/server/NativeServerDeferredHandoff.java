package dev.openallay.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;

/** Genuine public native schedule queue after the method rename; observation quanta must not execute recursively inline. */
final class NativeServerDeferredHandoff {
    private NativeServerDeferredHandoff() {}
    static void enqueue(MinecraftServer server, Runnable action) {
        if (!server.isSameThread()) throw new IllegalStateException("Deferred slice admission requires native owner");
        server.schedule(new TickTask(server.getTickCount(), action));
    }
}
