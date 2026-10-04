package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;

/** Native callback names/types are bound once; Screen feature painting stays shared. */
public abstract class GuideNativeScreen extends Screen {
    protected GuideNativeScreen(Component title) { super(title); }
    @Override public final void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        paintGuideScreen(new GuideGraphics(graphics), mouseX, mouseY, delta);
    }
    protected abstract void paintGuideScreen(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    @Override public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        paintGuideBackground(new GuideGraphics(graphics), mouseX, mouseY, delta);
    }
    protected void paintGuideBackground(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics.nativeGraphics(), mouseX, mouseY, delta);
    }
    protected final void renderGuideWidgets(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics.nativeGraphics(), mouseX, mouseY, delta);
    }
    @Override public final void resize(net.minecraft.client.Minecraft client, int width, int height) { resizeGuide(width, height); }
    protected void resizeGuide(int width, int height) { resizeGuideWidgets(width, height); }
    protected final void resizeGuideWidgets(int width, int height) { super.resize(minecraft, width, height); }
}
