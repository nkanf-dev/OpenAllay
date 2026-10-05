package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;

/** Native 1.21/1.21.1 frame interpolation for renderer-derived camera facts. */
public final class GuideNativeFrameTiming {
    private GuideNativeFrameTiming() {}
    public static float partialTick(Minecraft client) {
        return client.getTimer().getGameTimeDeltaPartialTick(true);
    }
}
