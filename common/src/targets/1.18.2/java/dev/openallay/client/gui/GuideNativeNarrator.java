package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.network.chat.Component;

/** Actual native singleton owns narrator status and immediate system speech in this family. */
public final class GuideNativeNarrator {
    private GuideNativeNarrator() {}
    public static boolean isActive(Minecraft client) { return NarratorChatListener.INSTANCE.isActive(); }
    public static void sayNow(Minecraft client, String text) { NarratorChatListener.INSTANCE.sayNow(text); }
    public static void sayNow(Minecraft client, Component text) { NarratorChatListener.INSTANCE.sayNow(text); }
}
