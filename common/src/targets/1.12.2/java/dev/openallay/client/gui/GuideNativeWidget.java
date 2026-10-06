package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.ITextComponent;

/** Actual legacy draw/input owner for product primitives. No feature or text algorithms are duplicated. */
public abstract class GuideNativeWidget extends GuiButton implements GuideWidget, GuideWidgetInput {
    public boolean active = true;
    private boolean focused;
    private final ITextComponent message;
    private GuideTooltip tooltip;
    protected GuideNativeWidget(int x, int y, int width, int height, ITextComponent message) {
        super(0, x, y, width, height, message.getFormattedText());
        this.message = message;
    }
    @Override public final void drawButton(Minecraft client, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        GuideGraphics graphics = GuideGraphics.wrap();
        graphics.paint(() -> {
            paintGuideWidget(graphics, mouseX, mouseY, delta);
            if (tooltip != null && isMouseOver(mouseX, mouseY)) graphics.setTooltipForNextFrame(tooltip.text(), mouseX, mouseY);
        });
    }
    protected abstract void paintGuideWidget(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    protected abstract void narrateGuideWidget(GuideNarration output);
    protected void onFocusedChanged(boolean focused) {}
    public final boolean isFocused() { return focused; }
    public final void setFocused(boolean focused) { guideSetFocused(focused); }
    @Override public final void guideSetFocused(boolean focused) {
        if (this.focused == focused) return;
        this.focused = focused;
        onFocusedChanged(focused);
    }
    @Override public final boolean guideIsFocused() { return focused; }
    public final boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
    public boolean guideMouseScrolled(double x, double y, double amount) { return false; }
    public final int getX() { return x; }
    public final int getY() { return y; }
    public final int getWidth() { return width; }
    public final int getHeight() { return height; }
    public final void setX(int x) { this.x = x; }
    public final void setY(int y) { this.y = y; }
    public final ITextComponent getMessage() { return message; }
    public final void setTooltip(GuideTooltip tooltip) { this.tooltip = tooltip; }
    public final Class<?> guideNativeType() { return getClass(); }
    public final boolean guideActive() { return active; }
    public final void guideActive(boolean active) { this.active = active; }
    public final boolean guideVisible() { return visible; }
    public final void guideVisible(boolean visible) { this.visible = visible; }
}
