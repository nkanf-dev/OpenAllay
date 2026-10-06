package dev.openallay.client;

import net.minecraft.client.Minecraft;

/** Actual 1.12 language and completed-frame metrics; no debug-text parsing. */
public final class MinecraftNativeClientFacts {
    private MinecraftNativeClientFacts() {}
    public static String selectedLanguage(Minecraft client) { return client.getLanguageManager().getCurrentLanguage().getLanguageCode(); }
    public static int fps(Minecraft client) { return Minecraft.getDebugFPS(); }
    public static long frameTimeNanos(Minecraft client) {
        var timer = client.getFrameTimer();
        long[] samples = timer.getFrames();
        return samples[Math.floorMod(timer.getIndex() - 1, samples.length)];
    }
}
