package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;

/** Primitive native input callbacks through 1.21.8; shared label/interaction decisions stay unchanged. */
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

    @Override public boolean keyPressed(int key, int scancode, int modifiers) { return guideKeyPressed(GuideNativeInput.capture(key, scancode, modifiers)); }
    @Override public boolean keyReleased(int key, int scancode, int modifiers) { return guideKeyReleased(GuideNativeInput.capture(key, scancode, modifiers)); }
    @Override public boolean charTyped(char character, int modifiers) { return guideCharTyped(GuideNativeInput.capture(character, modifiers)); }
    @Override public boolean mouseClicked(double x, double y, int button) { return guideMouseClicked(GuideNativeInput.capture(x, y, button), false); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return guideMouseDragged(GuideNativeInput.capture(x, y, button), dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { return guideMouseReleased(GuideNativeInput.capture(x, y, button)); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return guideMouseScrolled(x, y, vertical); }
    protected void onFocusedChanged(boolean focused) {}
    @Override public void setFocused(boolean focused) {
        boolean previous = isFocused();
        super.setFocused(focused);
        if (previous != focused) onFocusedChanged(focused);
    }
    protected final void guideSetBounds(int x, int y, int width, int height) {
        setWidth(width);
        setHeight(height);
        setX(x);
        setY(y);
    }
}
