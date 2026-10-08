package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;

/** Real IToast callback. Canonical card layout/lifetime remains GuideNativeToast. */
public abstract class GuideNativeToastBinding implements IToast {
    public abstract boolean finished();
    public abstract void onFinishedRendering();
    public abstract Object getToken();
    public abstract int width();
    public abstract int height();
    protected abstract boolean guideToastActive();
    protected abstract int guideSlotCount();
    protected abstract Visibility guideWantedVisibility();
    protected abstract void updateGuideToast(long fullyVisibleMillis);
    protected abstract void paintGuideToast(GuideGraphics graphics, FontRenderer font, long fullyVisibleMillis);
    public final int slotCount() { return guideSlotCount(); }
    @Override public final Object getType() { return getToken(); }
    @Override public final Visibility draw(GuiToast manager, long fullyVisibleMillis) {
        updateGuideToast(fullyVisibleMillis);
        if (guideToastActive()) {
            GuideGraphics graphics = GuideGraphics.wrap();
            graphics.paint(() -> paintGuideToast(graphics, manager.getMinecraft().fontRenderer, fullyVisibleMillis));
        }
        return guideWantedVisibility();
    }
}
