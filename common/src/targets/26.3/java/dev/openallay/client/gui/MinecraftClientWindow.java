package dev.openallay.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** Native client window ownership and HUD access for the Minecraft 26.3 family. */
public final class MinecraftClientWindow {
    private MinecraftClientWindow() {}
    /** Read-only native screen facts for bounded lifecycle diagnostics. Never closes or creates a screen. */
    public static java.util.Map<String, Object> screenFacts(Minecraft client) {
        var current = screen(client);
        java.util.Map<String, Object> facts = new java.util.LinkedHashMap<>();
        facts.put("screenClass", current == null ? "none" : current.getClass().getName());
        facts.put("screenPause", current != null && current.isPauseScreen());
        facts.put("playerPresent", playerPresent(client));
        facts.put("worldPresent", worldPresent(client));
        facts.put("windowFocused", focused(client));
        facts.put("overlayPresent", overlayPresent(client));
        return java.util.Map.copyOf(facts);
    }

    public static net.minecraft.client.server.IntegratedServer integratedServer(Minecraft client) { return client.getSingleplayerServer(); }
    public static String serverAddress(Minecraft client) { return client.getCurrentServer() == null ? null : client.getCurrentServer().ip; }
    public static int framebufferWidth(Minecraft client) { return mainRenderTarget(client).width; }
    public static int framebufferHeight(Minecraft client) { return mainRenderTarget(client).height; }
    public static int windowWidth(Minecraft client) { return client.getWindow().getWidth(); }
    public static int windowHeight(Minecraft client) { return client.getWindow().getHeight(); }
    public static int guiWidth(Minecraft client) { return client.getWindow().getGuiScaledWidth(); }
    public static int guiHeight(Minecraft client) { return client.getWindow().getGuiScaledHeight(); }

    public static Minecraft instance() { return Minecraft.getInstance(); }
    public static boolean ownerThread(Minecraft client) { return client.isSameThread(); }
    public static void execute(Minecraft client, Runnable action) { client.execute(action); }
    public static Object world(Minecraft client) { return client.level; }
    public static boolean worldPresent(Minecraft client) { return client.level != null; }
    public static boolean playerPresent(Minecraft client) { return client.player != null; }
    public static boolean active(Minecraft client) { return playerPresent(client) && worldPresent(client); }
    public static java.util.UUID actor(Minecraft client) { return client.player == null ? null : client.player.getUUID(); }
    public static void stop(Minecraft client) { client.stop(); }
    public static net.minecraft.client.gui.Font font(Minecraft client) { return client.font; }
    public static long gameTime(Minecraft client) { return client.level == null ? 0 : client.level.getGameTime(); }
    public static java.nio.file.Path gameDirectory(Minecraft client) { return client.gameDirectory.toPath(); }
    public static boolean focused(Minecraft client) { return client.isWindowActive(); }
    public static boolean overlayPresent(Minecraft client) { return overlay(client) != null; }


    /** Programmatic native resize. Each binding completes its own window notification contract. */
    public static void setWindowed(Minecraft minecraft, int width, int height) {
        minecraft.getWindow().setWindowed(width, height);
        // SDL setMode refreshes framebuffer dimensions before its queued resize event.
        // Finish the same native notification now, before this frame's GUI extraction.
        minecraft.framebufferSizeChanged();
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
