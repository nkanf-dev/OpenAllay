package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;

/** Native frame admission and older client/window flags; no Screen or capture logic is duplicated. */
public final class GuideNativeWindowState {
    private GuideNativeWindowState() {}
    public static boolean frameReady(Minecraft client) { return client.isGameLoadFinished(); }
    public static boolean teardownInProgress(Minecraft client) {
        return ((MinecraftTeardownState) client).openallay$teardownInProgress();
    }
    public static boolean debugScreenVisible(Minecraft client) { return client.gui.getDebugOverlay().showDebugScreen(); }
    public static boolean keyBindingScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof net.minecraft.client.gui.screens.controls.KeyBindsScreen;
    }
}
