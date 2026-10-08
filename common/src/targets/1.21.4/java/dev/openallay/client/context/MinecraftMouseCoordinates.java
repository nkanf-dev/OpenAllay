package dev.openallay.client.context;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.MouseHandler;

/** Native GUI mouse coordinates, using the game's own window scaling rule. */
public final class MinecraftMouseCoordinates {
    private MinecraftMouseCoordinates() {}
    public static double x(net.minecraft.client.Minecraft client) { return x(client.mouseHandler, client.getWindow()); }
    public static double y(net.minecraft.client.Minecraft client) { return y(client.mouseHandler, client.getWindow()); }
    public static boolean grabbed(net.minecraft.client.Minecraft client) { return client.mouseHandler.isMouseGrabbed(); }
    public static double x(MouseHandler mouse, Window window) { return mouse.xpos() * window.getGuiScaledWidth() / window.getScreenWidth(); }
    public static double y(MouseHandler mouse, Window window) { return mouse.ypos() * window.getGuiScaledHeight() / window.getScreenHeight(); }
}
