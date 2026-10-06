package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.Display;

/** Exact immediate-frame admission and native teardown observer; no fabricated modern window flags. */
public final class GuideNativeWindowState {
    private GuideNativeWindowState() {}
    public static boolean frameReady(Minecraft client) {
        return Display.isCreated() && !teardownInProgress(client);
    }
    public static boolean teardownInProgress(Minecraft client) {
        return ((MinecraftTeardownState) client).openallay$teardownInProgress();
    }
    public static boolean debugScreenVisible(Minecraft client) { return client.gameSettings.showDebugInfo; }
    public static boolean keyBindingScreen(GuiScreen screen) {
        return screen instanceof net.minecraft.client.gui.GuiControls;
    }
}
