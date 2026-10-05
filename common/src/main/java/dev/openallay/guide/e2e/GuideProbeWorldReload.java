package dev.openallay.guide.e2e;

import net.minecraft.client.Minecraft;

/** Current native world flows already own their cancellation screen callback. */
final class GuideProbeWorldReload {
    private GuideProbeWorldReload() {}
    static void tick(Minecraft client) {}
}
