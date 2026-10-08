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
        if (!client.isCallingFromMinecraftThread()) throw new IllegalStateException("Cursor callback requires the client owner thread");
        if (!Boolean.getBoolean(GuideClientE2EConfig.ENABLED)) throw new IllegalStateException("Development probe is disabled");
        net.minecraft.client.gui.GuiScreen owner = java.util.Objects.requireNonNull(client.currentScreen, "Native cursor screen unavailable");
        net.minecraft.client.entity.EntityPlayerSP player = client.player;
        net.minecraft.client.multiplayer.WorldClient world = client.world;
        final class $oaPattern0_Holder { net.minecraft.client.gui.GuiScreen value; GuideProbeMouseCallbacks bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (!((($oaPattern0_holder.value = owner) instanceof dev.openallay.guide.e2e.GuideProbeMouseCallbacks && (($oaPattern0_holder.bound = (GuideProbeMouseCallbacks) $oaPattern0_holder.value) != null)))) throw new IllegalStateException("Native GuiScreen callback binding unavailable");
        move(client, x, y);
        int button = $oaPattern0_holder.bound.openallay$heldButton();
        long pressedAt = $oaPattern0_holder.bound.openallay$lastMouseEvent();
        if (button < 0 || pressedAt <= 0 || !Mouse.isButtonDown(button)) return;
        int guiX = Mouse.getX() * owner.width / client.displayWidth;
        int guiY = owner.height - Mouse.getY() * owner.height / client.displayHeight - 1;
        long elapsed = Math.max(0, Minecraft.getSystemTime() - pressedAt);
        if (client.currentScreen != owner || client.player != player || client.world != world)
            throw new IllegalStateException("Native cursor owner changed");
        $oaPattern0_holder.bound.openallay$mouseClickMove(guiX, guiY, button, elapsed);
    }
    static double[] position(Minecraft client) {
        return new double[] {Mouse.getX(), client.displayHeight - 1 - Mouse.getY()};
    }
}
