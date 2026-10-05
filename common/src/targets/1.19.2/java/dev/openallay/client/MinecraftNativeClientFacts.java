package dev.openallay.client;

import dev.openallay.client.gui.mixin.MinecraftFpsAccess;
import net.minecraft.client.Minecraft;

/** 1.19.2 bindings for the active locale and native completed-frame metrics. */
public final class MinecraftNativeClientFacts {
    private MinecraftNativeClientFacts() {}
    public static String selectedLanguage(Minecraft client) { return client.getLanguageManager().getSelected().getCode(); }
    public static int fps(Minecraft client) { return MinecraftFpsAccess.openallay$fps(); }
    public static long frameTimeNanos(Minecraft client) {
        var timer = client.getFrameTimer();
        long[] samples = timer.getLog();
        return samples[Math.floorMod(timer.getLogEnd() - 1, samples.length)];
    }
}
