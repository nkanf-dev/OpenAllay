package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native widget callback binding, without duplicating label/interaction decisions. */
public abstract class GuideNativeWidget extends AbstractWidget {
    protected GuideNativeWidget(int x, int y, int width, int height, Component title) { super(x, y, width, height, title); }
    @Override protected final void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        GuideGraphics guide = GuideGraphics.wrap(graphics);
        guide.paint(() -> paintGuideWidget(guide, mouseX, mouseY, delta));
    }
    protected abstract void paintGuideWidget(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    public final void setTooltip(GuideTooltip tooltip) {
        super.setTooltip(tooltip == null ? null : net.minecraft.client.gui.components.Tooltip.create(tooltip.text()));
    }
    @Override protected final void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        narrateGuideWidget((part, text) -> output.add(net.minecraft.client.gui.narration.NarratedElementType.valueOf(part.name()), text));
    }
    protected abstract void narrateGuideWidget(GuideNarration output);
}
