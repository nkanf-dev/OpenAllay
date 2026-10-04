package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;

/** Actual immediate toast callback; completion comes from the native manager instance. */
public abstract class GuideNativeToastBinding implements Toast {
    public abstract boolean finished();
    public abstract void onFinishedRendering();

    @Override public final void render(GuiGraphics graphics, Font font, long fullyVisibleMillis) {
        GuideGraphics guide = new GuideGraphics(graphics);
        guide.paint(() -> paintGuideToast(guide, font, fullyVisibleMillis));
    }

    protected abstract void paintGuideToast(GuideGraphics graphics, Font font, long fullyVisibleMillis);
}
