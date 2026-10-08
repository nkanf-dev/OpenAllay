package dev.openallay.guide.e2e;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Native cursor boundary for the opt-in development probe. */
final class GuideProbeNativeCursor {
    private GuideProbeNativeCursor() {}
    static String backend() { return "glfw"; }
    static void move(Minecraft client, double x, double y) { GLFW.glfwSetCursorPos(client.getWindow().getWindow(), x, y); }
    static void dispatchMove(net.minecraft.client.Minecraft client, double x, double y) {
        try {
            var callback = client.mouseHandler.getClass().getDeclaredMethod("onMove", long.class, double.class, double.class);
            callback.setAccessible(true);
            callback.invoke(client.mouseHandler, client.getWindow().getWindow(), x, y);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Controlled native mouse callback failed", failure);
        }
    }
    static double[] position(Minecraft client) {
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(client.getWindow().getWindow(), x, y);
        return new double[] {x[0], y[0]};
    }
}
