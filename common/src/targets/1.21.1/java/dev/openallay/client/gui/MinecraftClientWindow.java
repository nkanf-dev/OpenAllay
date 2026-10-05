package dev.openallay.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** Native client window ownership and HUD access for the Minecraft 1.21/1.21.1 family. */
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
        return !GuideNativeWindowState.teardownInProgress(minecraft);
    }

    public static boolean hudHidden(Minecraft minecraft) {
        return minecraft.options.hideGui;
    }

    public static boolean debugScreenVisible(Minecraft minecraft) {
        return GuideNativeWindowState.debugScreenVisible(minecraft);
    }

    public static void extractDeferredSubtitles(Minecraft minecraft, GuideGraphics graphics) {
        ((dev.openallay.client.gui.mixin.GuiSubtitleAccess) minecraft.gui)
                .openallay$subtitleOverlay().render(graphics.nativeGraphics());
    }

    public static ToastComponent toastManager(Minecraft minecraft) {
        return minecraft.getToasts();
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
