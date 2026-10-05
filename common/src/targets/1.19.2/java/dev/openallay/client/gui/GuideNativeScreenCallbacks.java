package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.19.2 native vertical-only scroll and canvas-only background callbacks. */
abstract class GuideNativeScreenCallbacks extends Screen {
    protected GuideNativeScreenCallbacks(Component title) { super(title); }

    @Override public final boolean mouseScrolled(double x, double y, double vertical) {
        return guideMouseScrolled(x, y, 0.0, vertical);
    }
    public boolean guideMouseScrolled(double x, double y, double horizontal, double vertical) {
        return super.mouseScrolled(x, y, vertical);
    }
    protected void guideInitialFocus() {} // Native init has no automatic no-argument focus callback.
    protected abstract void clearGuideTooltipForNextRenderPass();
    protected final void clearNativeTooltipForNextRenderPass() {
        // This native Screen has no pending tooltip slot. The Guide paint scope owns it.
    }
    @Override public final void renderBackground(PoseStack graphics) {
        paintNativeGuideBackground(graphics, 0, 0, 0.0F);
    }
    protected abstract void paintNativeGuideBackground(PoseStack graphics, int mouseX, int mouseY, float delta);
    protected final void renderNativeGuideBackground(PoseStack graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics);
    }
    protected final void tickGuideWidgets() {
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.EditBox editor) editor.tick();
            else if (child instanceof net.minecraft.client.gui.components.MultiLineEditBox editor) editor.tick();
        }
    }
}
