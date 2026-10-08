package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/** Real native system narrator retains Minecraft mode and interruption ownership. */
public final class GuideNativeNarrator {
    private GuideNativeNarrator() {}
    public static boolean isActive(Minecraft client) { return NarratorChatListener.INSTANCE.isActive(); }
    public static void sayNow(Minecraft client, String text) { sayNow(client, new TextComponentString(text)); }
    public static void sayNow(Minecraft client, ITextComponent text) { NarratorChatListener.INSTANCE.say(ChatType.SYSTEM, text); }
}
