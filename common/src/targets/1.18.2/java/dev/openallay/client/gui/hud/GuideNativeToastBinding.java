package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;

/**
 * Native 1.18.2 render callback for core compilation diagnostics.
 * Two-slot admission and 64px placement require the actual native manager body before game acceptance.
 * guideSlotCount remains an owned card fact; this native Toast API has no slotCount method.
 */
public abstract class GuideNativeToastBinding implements Toast {
    public abstract boolean finished();
    protected abstract boolean guideToastActive();
    public abstract void onFinishedRendering();
    protected abstract int guideSlotCount();
    protected abstract Visibility guideWantedVisibility();
    protected abstract void updateGuideToast(long fullyVisibleMillis);

    @Override public final Visibility render(PoseStack graphics, ToastComponent component, long fullyVisibleMillis) {
        updateGuideToast(fullyVisibleMillis);
        if (!guideToastActive()) return guideWantedVisibility();
        GuideGraphics guide = GuideGraphics.wrap(graphics);
        guide.paint(() -> paintGuideToast(guide, component.getMinecraft().font, fullyVisibleMillis));
        return guideWantedVisibility();
    }

    protected abstract void paintGuideToast(GuideGraphics graphics, Font font, long fullyVisibleMillis);
}
