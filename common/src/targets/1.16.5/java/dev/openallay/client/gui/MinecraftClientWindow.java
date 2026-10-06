package dev.openallay.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** Native client window ownership and HUD access for the Minecraft 1.21/1.21.1 family. */
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
    public static String serverAddress(Minecraft client) { return client.getCurrentServerData() == null ? null : client.getCurrentServerData().ip; }
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
        GuideNativeWindowResize.class.cast(minecraft.getWindow()).openallay$windowedSize(width, height);
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
