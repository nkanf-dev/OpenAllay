package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native widget callback binding, without duplicating label/interaction decisions. */
public abstract class GuideNativeWidget extends AbstractWidget {
    protected GuideNativeWidget(int x, int y, int width, int height, Component title) { super(x, y, width, height, title); }
    @Override protected final void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        paintGuideWidget(new GuideGraphics(graphics), mouseX, mouseY, delta);
    }
    protected abstract void paintGuideWidget(GuideGraphics graphics, int mouseX, int mouseY, float delta);
}
