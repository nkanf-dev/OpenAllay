package dev.openallay.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** Native client window ownership and HUD access for the Minecraft 26.2/26.3 family. */
public final class MinecraftClientWindow {
    private MinecraftClientWindow() {}

    /** Programmatic native resize. Each binding completes its own window notification contract. */
    public static void setWindowed(Minecraft minecraft, int width, int height) {
        minecraft.getWindow().setWindowed(width, height);
    }


    public static Screen screen(Minecraft minecraft) {
        return minecraft.gui.screen();
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.gui.setScreen(screen);
    }

    public static Overlay overlay(Minecraft minecraft) {
        return minecraft.gui.overlay();
    }

    public static boolean canInterruptScreen(Minecraft minecraft) {
        return minecraft.gui.canInterruptScreen();
    }

    public static boolean hudHidden(Minecraft minecraft) {
        return minecraft.gui.hud.isHidden();
    }

    public static boolean debugScreenVisible(Minecraft minecraft) {
        return minecraft.gui.hud.getDebugOverlay().showDebugScreen();
    }

    public static void extractDeferredSubtitles(Minecraft minecraft, GuideGraphics graphics) {
        minecraft.gui.hud.extractDeferredSubtitles();
    }

    public static ToastManager toastManager(Minecraft minecraft) {
        return minecraft.gui.toastManager();
    }

    public static RenderTarget mainRenderTarget(Minecraft minecraft) {
        return minecraft.gameRenderer.mainRenderTarget();
    }
    public static net.minecraft.client.Camera camera(Minecraft client) { return client.gameRenderer.mainCamera(); }
    public static net.minecraft.client.renderer.state.GameRenderState renderState(Minecraft client) { return client.gameRenderer.gameRenderState(); }
    public static void showScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreenAndShow(screen);
    }
    public static boolean isInGameUi(Minecraft minecraft, Screen screen) {
        return screen.isInGameUi();
    }
}
