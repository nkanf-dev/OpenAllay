package dev.openallay.client.gui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

/** Real legacy GuiToast/font access; canonical notification algorithms stay shared. */
public final class GuideNativeToastAccess {
    private GuideNativeToastAccess() {}
    public static FontRenderer font(Minecraft client) { return client.fontRenderer; }
    public static int guiWidth(Minecraft client) { return new ScaledResolution(client).getScaledWidth(); }
    public static void add(Minecraft client, GuideNativeToast toast) { client.getToastGui().add(toast); }
}
