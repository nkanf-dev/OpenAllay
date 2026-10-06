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
