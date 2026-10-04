package dev.openallay.guide.e2e;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;

/** Native cursor boundary for the opt-in development probe. */
final class GuideProbeNativeCursor {
    private GuideProbeNativeCursor() {}
    static String backend() { return "glfw"; }
    static void move(Window window, double x, double y) { GLFW.glfwSetCursorPos(window.getWindow(), x, y); }
    static void dispatchMove(net.minecraft.client.Minecraft client, double x, double y) {
        try {
            var callback = client.mouseHandler.getClass().getDeclaredMethod("onMove", long.class, double.class, double.class);
            callback.setAccessible(true);
            callback.invoke(client.mouseHandler, client.getWindow().getWindow(), x, y);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Controlled native mouse callback failed", failure);
        }
    }
    static double[] position(Window window) {
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(window.getWindow(), x, y);
        return new double[] {x[0], y[0]};
    }
}
