package dev.openallay.client.context;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
public final class MinecraftMouseCoordinates {
    private MinecraftMouseCoordinates() {}
    public static double x(Minecraft client) { return Mouse.getX()*new ScaledResolution(client).getScaledWidth()/(double)client.displayWidth; }
    public static double y(Minecraft client) { return new ScaledResolution(client).getScaledHeight()-Mouse.getY()*new ScaledResolution(client).getScaledHeight()/(double)client.displayHeight-1; }
    public static boolean grabbed(Minecraft client) { return Mouse.isGrabbed(); }
}
