package dev.openallay.guide.e2e;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLMouse;

/** SDL native cursor boundary; scenario decisions and receipts remain shared. */
final class GuideProbeNativeCursor {
    private GuideProbeNativeCursor() {}
    static String backend() { return "sdl"; }
    static void move(Window window, double x, double y) {
        SDLMouse.SDL_WarpMouseInWindow(window.handle(), (float) x, (float) y);
    }
    static void dispatchMove(net.minecraft.client.Minecraft client, double x, double y) {
        client.mouseHandler.onMove(client.getWindow().handle(), x, y, 0.0, 0.0);
    }
    static double[] position(Window window) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var x = stack.mallocFloat(1);
            var y = stack.mallocFloat(1);
            SDLMouse.SDL_GetMouseState(x, y);
            return new double[] {x.get(0), y.get(0)};
        }
    }
}
