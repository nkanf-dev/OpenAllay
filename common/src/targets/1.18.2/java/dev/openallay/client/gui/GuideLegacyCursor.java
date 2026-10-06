package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Native hand cursor marks the draggable resize grip on GLFW 3.3. */
final class GuideLegacyCursor {
    private static long resizeCursor;
    private GuideLegacyCursor() {}
    static void beginFrame() { GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), 0); }
    static void requestResize() {
        if (resizeCursor == 0) resizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
        if (resizeCursor != 0) GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), resizeCursor);
    }
    static void close() {
        beginFrame();
        if (resizeCursor != 0) { GLFW.glfwDestroyCursor(resizeCursor); resizeCursor = 0; }
    }
}
