package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native Button draw and scrolling-label binding. */
public abstract class GuideNativeButton extends Button {
    protected GuideNativeButton(int x, int y, int width, int height, Component title, OnPress press, GuideButtonNarration narration) {
        super(x, y, width, height, title, press, narration::create);
    }
    @Override protected final void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        GuideGraphics guide = GuideGraphics.wrap(graphics);
        guide.paint(() -> paintGuideButton(guide, mouseX, mouseY, delta));
    }
    protected abstract void paintGuideButton(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    protected final void paintGuideButtonLabel(GuideGraphics graphics, Component label, int padding) {
        extractScrollingStringOverContents(graphics.nativeGraphics().textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE), label, padding);
    }
    public final void setTooltip(GuideTooltip tooltip) {
        super.setTooltip(tooltip == null ? null : net.minecraft.client.gui.components.Tooltip.create(tooltip.text()));
    }
}
