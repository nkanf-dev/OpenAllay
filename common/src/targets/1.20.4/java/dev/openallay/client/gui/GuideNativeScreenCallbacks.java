package dev.openallay.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.20.4/1.20.3 Screen callbacks before the native tooltip clear and automatic focus methods. */
abstract class GuideNativeScreenCallbacks extends Screen {
    protected GuideNativeScreenCallbacks(Component title) { super(title); }

    @Override public final boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return guideMouseScrolled(x, y, horizontal, vertical);
    }
    public boolean guideMouseScrolled(double x, double y, double horizontal, double vertical) {
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    protected void guideInitialFocus() {} // Native init has no automatic no-argument focus callback.
    protected abstract void clearGuideTooltipForNextRenderPass();
    protected final void clearNativeTooltipForNextRenderPass() {
        // Owned Screen.renderWithTooltip is routed directly to render by ScreenTooltipScopeMixin.
        // Native Screen has no clear method; the shared Guide paint scope owns its pending tooltip.
    }
    @Override public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        paintNativeGuideBackground(graphics, mouseX, mouseY, delta);
    }
    protected abstract void paintNativeGuideBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta);
    protected final void renderNativeGuideBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
    }
    protected final void tickGuideWidgets() {} // Native widgets blink from elapsed time in this family.
}
