package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Actual immediate system narration; the native narrator keeps status and interruption rules. */
public final class GuideNativeNarrator {
    private GuideNativeNarrator() {}
    public static boolean isActive(Minecraft client) { return client.getNarrator().isActive(); }
    public static void sayNow(Minecraft client, String text) { client.getNarrator().saySystemNow(text); }
    public static void sayNow(Minecraft client, Component text) { client.getNarrator().saySystemNow(text); }
}
