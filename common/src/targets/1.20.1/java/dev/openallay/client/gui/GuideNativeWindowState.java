package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;

/** 1.20.1 has no game-load latch or Gui debug getter; use actual world/window and Options facts. */
public final class GuideNativeWindowState {
    private GuideNativeWindowState() {}
    public static boolean frameReady(Minecraft client) {
        return client.isRunning() && client.level != null && client.player != null
                && !teardownInProgress(client);
    }
    public static boolean teardownInProgress(Minecraft client) {
        return ((MinecraftTeardownState) client).openallay$teardownInProgress();
    }
    public static boolean debugScreenVisible(Minecraft client) { return client.options.renderDebug; }
    public static boolean keyBindingScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof net.minecraft.client.gui.screens.controls.KeyBindsScreen;
    }
}
