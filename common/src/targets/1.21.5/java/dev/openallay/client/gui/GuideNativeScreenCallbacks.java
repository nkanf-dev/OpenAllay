package dev.openallay.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Immediate Screen callbacks; paint and tooltip scopes stay in one inherited binding. */
abstract class GuideNativeScreenCallbacks extends Screen {
    protected GuideNativeScreenCallbacks(Component title) { super(title); }

    @Override public final boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return guideMouseScrolled(x, y, horizontal, vertical);
    }
    public boolean guideMouseScrolled(double x, double y, double horizontal, double vertical) {
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override protected final void setInitialFocus() { guideInitialFocus(); }
    protected void guideInitialFocus() { super.setInitialFocus(); }
    @Override protected final void clearTooltipForNextRenderPass() { clearGuideTooltipForNextRenderPass(); }
    protected abstract void clearGuideTooltipForNextRenderPass();
    protected final void clearNativeTooltipForNextRenderPass() { super.clearTooltipForNextRenderPass(); }
    @Override public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        paintNativeGuideBackground(graphics, mouseX, mouseY, delta);
    }
    protected abstract void paintNativeGuideBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta);
    protected final void renderNativeGuideBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
    }
    protected final void tickGuideWidgets() {} // Native widgets blink from elapsed time in this family.
}
