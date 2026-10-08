package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;

/** Actual completed native interpolation value, not modern DeltaTracker or frame-time alias. */
public final class GuideNativeFrameTiming {
    private GuideNativeFrameTiming() {}
    public static float partialTick(Minecraft client) { return client.getRenderPartialTicks(); }
}
