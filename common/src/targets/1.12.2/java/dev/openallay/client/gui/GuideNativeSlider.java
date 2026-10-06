package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.client.config.GuiSlider;

/** Real Forge GuiSlider owns normalized value and dragging; canonical settings own ranges/messages. */
public abstract class GuideNativeSlider extends GuiSlider implements GuideWidgetInput {
    protected double value;
    public boolean active = true;
    private ITextComponent message;
    private GuideTooltip tooltip;
    private boolean focused;
    protected GuideNativeSlider(int x, int y, int width, int height, ITextComponent text, double value) {
        super(0, x, y, width, height, "", "", 0.0, 1.0, value, false, false);
        this.value = value;
        this.message = text;
    }
    protected abstract void updateMessage();
    protected abstract void applyValue();
    private void synchronizeValue() {
        if (value != sliderValue) {
            value = sliderValue;
            updateMessage();
            applyValue();
        }
    }
    @Override public boolean mousePressed(Minecraft client, int mouseX, int mouseY) {
        enabled = active;
        boolean handled = super.mousePressed(client, mouseX, mouseY);
        if (handled) synchronizeValue();
        return handled;
    }
    @Override public void drawButton(Minecraft client, int mouseX, int mouseY, float delta) {
        enabled = active;
        super.drawButton(client, mouseX, mouseY, delta);
        synchronizeValue();
        if (tooltip != null && visible && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            GuideGraphics graphics = GuideGraphics.wrap();
            graphics.paint(() -> graphics.setTooltipForNextFrame(tooltip.text(), mouseX, mouseY));
        }
    }
    public final void setMessage(ITextComponent message) { this.message = message; displayString = message.getFormattedText(); }
    public final ITextComponent getMessage() { return message; }
    public final int getX() { return x; }
    public final int getY() { return y; }
    public final int getWidth() { return width; }
    public final int getHeight() { return height; }
    public final void setX(int value) { x = value; }
    public final void setY(int value) { y = value; }
    public final void setTooltip(GuideTooltip tooltip) { this.tooltip = tooltip; }
    public final void onClick(double x, double y) { mousePressed(Minecraft.getMinecraft(), (int) x, (int) y); }
    public final void onRelease(double x, double y) { super.mouseReleased((int) x, (int) y); }
    @Override public final void guideSetFocused(boolean focused) { this.focused = focused; }
    @Override public final boolean guideIsFocused() { return focused; }
}
