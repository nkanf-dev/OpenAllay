package dev.openallay.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** Native client window ownership and HUD access for the Minecraft 26.1 family. */
public final class MinecraftClientWindow {
    private MinecraftClientWindow() {}

    public static Screen screen(Minecraft minecraft) {
        return minecraft.screen;
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreen(screen);
    }

    public static Overlay overlay(Minecraft minecraft) {
        return minecraft.getOverlay();
    }

    public static boolean canInterruptScreen(Minecraft minecraft) {
        return !((dev.openallay.client.gui.mixin.MinecraftTeardownAccess) minecraft).openallay$teardownInProgress();
    }

    public static boolean hudHidden(Minecraft minecraft) {
        return minecraft.options.hideGui;
    }

    public static boolean debugScreenVisible(Minecraft minecraft) {
        return minecraft.gui.getDebugOverlay().showDebugScreen();
    }

    public static void extractDeferredSubtitles(Minecraft minecraft, GuideGraphics graphics) {
        ((dev.openallay.client.gui.mixin.GuiSubtitleAccess) minecraft.gui)
                .openallay$renderSubtitles(graphics.nativeGraphics(), minecraft.getDeltaTracker());
    }

    public static ToastManager toastManager(Minecraft minecraft) {
        return minecraft.getToastManager();
    }

    public static RenderTarget mainRenderTarget(Minecraft minecraft) {
        return minecraft.getMainRenderTarget();
    }
    public static net.minecraft.client.Camera camera(Minecraft client) { return client.gameRenderer.getMainCamera(); }
    public static void showScreen(Minecraft minecraft, Screen screen) {
        minecraft.forceSetScreen(screen);
    }
    public static boolean isInGameUi(Minecraft minecraft, Screen screen) {
        return screen instanceof GuideNativeScreen guide ? guide.isInGameUi()
                : minecraft.level != null && !screen.isPauseScreen();
    }
}
