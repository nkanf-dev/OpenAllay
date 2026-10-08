package dev.openallay.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;

/** Genuine older public native tell queue; observation quanta must not execute recursively inline. */
final class NativeServerDeferredHandoff {
    private NativeServerDeferredHandoff() {}
    static void enqueue(MinecraftServer server, Runnable action) {
        if (!server.isSameThread()) throw new IllegalStateException("Deferred slice admission requires native owner");
        server.tell(new TickTask(server.getTickCount(), action));
    }
}
