package dev.openallay.guide.e2e;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;

/** Actual LWJGL2 cursor coordinates. The public boundary uses top-left client pixels. */
final class GuideProbeNativeCursor {
    private GuideProbeNativeCursor() {}
    static String backend() { return "lwjgl2"; }
    static void move(Minecraft client, double x, double y) {
        Mouse.setCursorPosition((int) Math.round(x), client.displayHeight - 1 - (int) Math.round(y));
    }
    static void dispatchMove(Minecraft client, double x, double y) {
        throw new UnsupportedOperationException("LWJGL2 has no MouseHandler cursor callback; native input frames own cursor observation");
    }
    static double[] position(Minecraft client) {
        return new double[] {Mouse.getX(), client.displayHeight - 1 - Mouse.getY()};
    }
}
