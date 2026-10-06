package dev.openallay.guide.e2e;

import net.minecraft.client.Minecraft;

/** The actual 1.12 integrated launch loads the existing native save synchronously. */
final class GuideProbeWorldReload {
    private GuideProbeWorldReload() {}
    static void open(Minecraft client, String name, Runnable cancelled) {
        client.launchIntegratedServer(name, name, null);
    }
    static void tick(Minecraft client) {}
}
