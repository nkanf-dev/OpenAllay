package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;

/** Native widget callback binding, without duplicating label/interaction decisions. */
public abstract class GuideNativeWidget extends AbstractWidget {
    protected GuideNativeWidget(int x, int y, int width, int height, Component title) { super(x, y, width, height, title); }
    @Override protected final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        paintGuideWidget(GuideGraphics.wrap(graphics), mouseX, mouseY, delta);
    }
    protected abstract void paintGuideWidget(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    public final void setTooltip(GuideTooltip tooltip) {
        super.setTooltip(tooltip == null ? null : net.minecraft.client.gui.components.Tooltip.create(tooltip.text()));
    }
    @Override protected final void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        narrateGuideWidget((part, text) -> output.add(net.minecraft.client.gui.narration.NarratedElementType.valueOf(part.name()), text));
    }
    protected abstract void narrateGuideWidget(GuideNarration output);

    public boolean guideKeyPressed(GuideInputKey event) { return false; }
    public boolean guideKeyReleased(GuideInputKey event) { return false; }
    public boolean guideCharTyped(GuideInputCharacter event) { return false; }
    public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) { return false; }
    public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) { return false; }
    public boolean guideMouseReleased(GuideInputMouse event) { return false; }
    public boolean guideMouseScrolled(double x, double y, double amount) { return false; }

    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return guideKeyPressed(GuideNativeInput.capture(event)); }
    @Override public boolean keyReleased(net.minecraft.client.input.KeyEvent event) { return guideKeyReleased(GuideNativeInput.capture(event)); }
    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event) { return guideCharTyped(GuideNativeInput.capture(event)); }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { return guideMouseClicked(GuideNativeInput.capture(event), doubleClick); }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) { return guideMouseDragged(GuideNativeInput.capture(event), dx, dy); }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) { return guideMouseReleased(GuideNativeInput.capture(event)); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return guideMouseScrolled(x, y, vertical); }
    protected void onFocusedChanged(boolean focused) {}
    @Override public void setFocused(boolean focused) {
        boolean previous = isFocused();
        super.setFocused(focused);
        if (previous != focused) onFocusedChanged(focused);
    }
    protected final void guideSetBounds(int x, int y, int width, int height) {
        setRectangle(width, height, x, y);
    }
}
