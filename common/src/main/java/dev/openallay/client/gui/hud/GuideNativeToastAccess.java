package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.MinecraftClientWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/** Native toast primitive access, not notification/card algorithms. */
public final class GuideNativeToastAccess {
    private GuideNativeToastAccess() {}
    public static Font font(Minecraft client) { return client.font; }
    public static int guiWidth(Minecraft client) { return client.getWindow().getGuiScaledWidth(); }
    public static void add(Minecraft client, GuideNativeToast toast) { MinecraftClientWindow.toastManager(client).addToast(toast); }
}
