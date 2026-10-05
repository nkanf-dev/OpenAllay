package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;

/** Native 1.20.6/1.20.5 frame interpolation; this family has no DeltaTracker type. */
public final class GuideNativeFrameTiming {
    private GuideNativeFrameTiming() {}
    public static float partialTick(Minecraft client) {
        return client.getFrameTime();
    }
}
