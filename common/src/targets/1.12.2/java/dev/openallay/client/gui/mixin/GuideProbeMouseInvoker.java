package dev.openallay.client.gui.mixin;

import dev.openallay.guide.e2e.GuideProbeMouseCallbacks;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Bind the actual LWJGL2 GuiScreen event state and native drag callback. */
@Mixin(GuiScreen.class)
public interface GuideProbeMouseInvoker extends GuideProbeMouseCallbacks {
    @Override @Accessor("eventButton") int openallay$heldButton();
    @Override @Accessor("lastMouseEvent") long openallay$lastMouseEvent();
    @Override @Invoker("mouseClickMove") void openallay$mouseClickMove(int x, int y, int heldButton, long elapsed);
}
