package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native Toast render callback; lifetime and card layout remain shared. */
public abstract class GuideNativeToastBinding implements Toast {
    public abstract boolean finished();
    @Override public final void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleMillis) {
        paintGuideToast(new GuideGraphics(graphics), font, fullyVisibleMillis);
    }
    protected abstract void paintGuideToast(GuideGraphics graphics, Font font, long fullyVisibleMillis);
}
