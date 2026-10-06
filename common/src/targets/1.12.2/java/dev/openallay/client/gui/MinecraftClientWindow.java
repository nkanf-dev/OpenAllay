package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/** Actual 1.12.2 client window/HUD ownership. No fabricated Window or Overlay object. */
public final class MinecraftClientWindow {
    private MinecraftClientWindow() {}
    /** Read-only native screen facts for bounded lifecycle diagnostics. Never closes or creates a screen. */
    public static java.util.Map<String, Object> screenFacts(Minecraft client) {
        var current = screen(client);
        java.util.Map<String, Object> facts = new java.util.LinkedHashMap<>();
        facts.put("screenClass", current == null ? "none" : current.getClass().getName());
        facts.put("screenPause", current != null && current.doesGuiPauseGame());
        facts.put("playerPresent", playerPresent(client));
        facts.put("worldPresent", worldPresent(client));
        facts.put("windowFocused", focused(client));
        facts.put("overlayPresent", overlayPresent(client));
        return java.util.Map.copyOf(facts);
    }

    public static net.minecraft.server.integrated.IntegratedServer integratedServer(Minecraft client) { return client.getIntegratedServer(); }
    public static String serverAddress(Minecraft client) { return client.getCurrentServerData() == null ? null : client.getCurrentServerData().serverIP; }
    public static int framebufferWidth(Minecraft client) { return client.getFramebuffer().framebufferWidth; }
    public static int framebufferHeight(Minecraft client) { return client.getFramebuffer().framebufferHeight; }
    public static int windowWidth(Minecraft client) { return org.lwjgl.opengl.Display.getWidth(); }
    public static int windowHeight(Minecraft client) { return org.lwjgl.opengl.Display.getHeight(); }

    public static Minecraft instance() { return Minecraft.getMinecraft(); }
    public static boolean ownerThread(Minecraft client) { return client.isCallingFromMinecraftThread(); }
    public static void execute(Minecraft client, Runnable action) { client.addScheduledTask(action); }
    public static Object world(Minecraft client) { return client.world; }
    public static boolean worldPresent(Minecraft client) { return client.world != null; }
    public static boolean playerPresent(Minecraft client) { return client.player != null; }
    public static boolean active(Minecraft client) { return playerPresent(client) && worldPresent(client); }
    public static java.util.UUID actor(Minecraft client) { return client.player == null ? null : client.player.getUniqueID(); }
    public static void stop(Minecraft client) { client.shutdown(); }
    public static net.minecraft.client.gui.FontRenderer font(Minecraft client) { return client.fontRenderer; }
    public static long gameTime(Minecraft client) { return client.world == null ? 0 : client.world.getTotalWorldTime(); }
    public static java.nio.file.Path gameDirectory(Minecraft client) { return client.mcDataDir.toPath(); }
    public static boolean overlayPresent(Minecraft client) { return false; }
    public static boolean canInterruptScreen(Minecraft client) { return !GuideNativeWindowState.teardownInProgress(client); }

    public static GuiScreen screen(Minecraft client) { return client.currentScreen; }
    public static void setScreen(Minecraft client, GuiScreen screen) { client.displayGuiScreen(screen); }
    public static void showScreen(Minecraft client, GuiScreen screen) { client.displayGuiScreen(screen); }
    public static GuiToast toastManager(Minecraft client) { return client.getToastGui(); }
    public static Framebuffer mainRenderTarget(Minecraft client) { return client.getFramebuffer(); }
    public static boolean hudHidden(Minecraft client) { return client.gameSettings.hideGUI; }
    public static boolean debugScreenVisible(Minecraft client) { return client.gameSettings.showDebugInfo; }
    public static boolean focused(Minecraft client) { return Display.isActive(); }
    public static int guiWidth(Minecraft client) { return new net.minecraft.client.gui.ScaledResolution(client).getScaledWidth(); }
    public static int guiHeight(Minecraft client) { return new net.minecraft.client.gui.ScaledResolution(client).getScaledHeight(); }
    /** Native display resize followed by Minecraft framebuffer and GuiScreen notification. */
    public static void setWindowed(Minecraft client, int width, int height) {
        ((GuideNativeWindowResize) client).openallay$windowedSize(width, height);
    }
    public static void extractDeferredSubtitles(Minecraft client, GuideGraphics graphics) {
        GuideNativeSubtitleRender.render(client, graphics);
    }
}
