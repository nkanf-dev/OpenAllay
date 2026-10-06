package dev.openallay.guide.e2e;

import net.minecraft.client.Minecraft;

/** The actual 1.12 integrated launch loads the existing native save synchronously. */
final class GuideProbeWorldReload {
    private GuideProbeWorldReload() {}
    static void open(Minecraft client, String name, Runnable cancelled) {
        if (!client.isCallingFromMinecraftThread()) throw new IllegalStateException("World reload requires the client owner thread");
        client.launchIntegratedServer(name, name, null);
        // Native launch returns early when StartupQuery cancels or the server stops.
        if (client.getIntegratedServer() == null) cancelled.run();
    }
    static void tick(Minecraft client) {}
}
