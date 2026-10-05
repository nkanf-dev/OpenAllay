package dev.openallay.client;

import net.minecraft.client.Minecraft;

/** Small native bindings for facts captured from the active client. */
public final class MinecraftNativeClientFacts {
    private MinecraftNativeClientFacts() {}
    public static String selectedLanguage(Minecraft client) { return client.getLanguageManager().getSelected(); }
    public static int fps(Minecraft client) { return client.getFps(); }
    public static long frameTimeNanos(Minecraft client) { return client.getFrameTimeNs(); }
}
