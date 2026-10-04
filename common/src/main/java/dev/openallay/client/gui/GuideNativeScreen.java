package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native callback names/types are bound once; Screen feature painting stays shared. */
public abstract class GuideNativeScreen extends Screen {
    protected GuideNativeScreen(Component title) { super(title); }
    @Override public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        paintGuideScreen(new GuideGraphics(graphics), mouseX, mouseY, delta);
    }
    protected abstract void paintGuideScreen(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    @Override public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        paintGuideBackground(new GuideGraphics(graphics), mouseX, mouseY, delta);
    }
    protected void paintGuideBackground(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics.nativeGraphics(), mouseX, mouseY, delta);
    }
    protected final void renderGuideWidgets(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics.nativeGraphics(), mouseX, mouseY, delta);
    }
}
