package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;

/** Native 1.21/1.21.1 callback: the manager requests visibility from each actual render. */
public abstract class GuideNativeToastBinding implements Toast {
    public abstract boolean finished();
    protected abstract boolean guideToastActive();
    public abstract void onFinishedRendering();
    protected abstract int guideSlotCount();
    protected abstract Visibility guideWantedVisibility();
    protected abstract void updateGuideToast(long fullyVisibleMillis);

    @Override public final int slotCount() { return guideSlotCount(); }
    @Override public final Visibility render(PoseStack graphics, ToastComponent component, long fullyVisibleMillis) {
        updateGuideToast(fullyVisibleMillis);
        if (!guideToastActive()) return guideWantedVisibility();
        GuideGraphics guide = GuideGraphics.wrap(graphics);
        guide.paint(() -> paintGuideToast(guide, component.getMinecraft().font, fullyVisibleMillis));
        return guideWantedVisibility();
    }

    protected abstract void paintGuideToast(GuideGraphics graphics, Font font, long fullyVisibleMillis);
}
