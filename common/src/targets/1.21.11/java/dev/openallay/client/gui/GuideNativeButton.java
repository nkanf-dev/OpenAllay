package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;

/** Native Button draw and scrolling-label binding. */
public abstract class GuideNativeButton extends Button {
    protected GuideNativeButton(int x, int y, int width, int height, Component title, java.util.function.Consumer<OpenAllayButton> press, GuideButtonNarration narration) {
        super(x, y, width, height, title, button -> press.accept((OpenAllayButton) button), defaults -> (net.minecraft.network.chat.MutableComponent) narration.create(defaults));
    }
    @Override protected final void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        paintGuideButton(GuideGraphics.wrap(graphics), mouseX, mouseY, delta);
    }
    protected abstract void paintGuideButton(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    public final boolean isGuideHovered() { return isHovered(); }
    protected final void paintGuideButtonLabel(GuideGraphics graphics, Component label, int padding) {
        renderScrollingStringOverContents(graphics.nativeGraphics().textRendererForWidget(this, GuiGraphics.HoveredTextEffects.NONE), label, padding);
    }
    public final void setTooltip(GuideTooltip tooltip) {
        super.setTooltip(tooltip == null ? null : net.minecraft.client.gui.components.Tooltip.create(tooltip.text()));
    }
}
