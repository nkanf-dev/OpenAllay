package dev.openallay.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** Native client window ownership and HUD access for the Minecraft 26.1 family. */
public final class MinecraftClientWindow {
    private MinecraftClientWindow() {}

    /** Programmatic native resize. Each binding completes its own window notification contract. */
    public static void setWindowed(Minecraft minecraft, int width, int height) {
        minecraft.getWindow().setWindowed(width, height);
    }


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
        return minecraft.canInterruptScreen();
    }

    public static boolean hudHidden(Minecraft minecraft) {
        return minecraft.options.hideGui;
    }

    public static boolean debugScreenVisible(Minecraft minecraft) {
        return minecraft.gui.getDebugOverlay().showDebugScreen();
    }

    public static void extractDeferredSubtitles(Minecraft minecraft, GuideGraphics graphics) {
        minecraft.gui.extractDeferredSubtitles();
    }

    public static ToastManager toastManager(Minecraft minecraft) {
        return minecraft.getToastManager();
    }

    public static RenderTarget mainRenderTarget(Minecraft minecraft) {
        return minecraft.getMainRenderTarget();
    }
    public static net.minecraft.client.Camera camera(Minecraft client) { return client.gameRenderer.getMainCamera(); }
    public static net.minecraft.client.renderer.state.GameRenderState renderState(Minecraft client) { return client.gameRenderer.getGameRenderState(); }
    public static void showScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreenAndShow(screen);
    }
    public static boolean isInGameUi(Minecraft minecraft, Screen screen) {
        return screen.isInGameUi();
    }
}
