package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native Toast render callback; lifetime and card layout remain shared. */
public abstract class GuideNativeToastBinding implements Toast {
    public abstract boolean finished();
    protected abstract boolean guideToastActive();
    protected abstract int guideSlotCount();
    protected abstract Visibility guideWantedVisibility();
    protected abstract void updateGuideToast(long fullyVisibleMillis);

    @Override public final int occcupiedSlotCount() { return guideSlotCount(); }
    @Override public final Visibility getWantedVisibility() { return guideWantedVisibility(); }
    @Override public final void update(ToastManager manager, long fullyVisibleMillis) {
        updateGuideToast(fullyVisibleMillis);
    }

    @Override public final void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleMillis) {
        if (!guideToastActive()) return;
        paintGuideToast(GuideGraphics.wrap(graphics), font, fullyVisibleMillis);
    }
    protected abstract void paintGuideToast(GuideGraphics graphics, Font font, long fullyVisibleMillis);
}
