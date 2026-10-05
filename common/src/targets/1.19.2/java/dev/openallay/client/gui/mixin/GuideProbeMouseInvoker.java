package dev.openallay.client.gui.mixin;

import dev.openallay.guide.e2e.GuideProbeMouseCallbacks;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exact 1.19.2 native cursor callback; the configured refmap owns runtime remapping. */
@Mixin(MouseHandler.class)
public interface GuideProbeMouseInvoker extends GuideProbeMouseCallbacks {
    @Override
    @Invoker("onMove")
    void openallay$onMove(long window, double x, double y);
}
