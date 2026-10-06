package dev.openallay.client.gui;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.ITextComponent;

/** Actual GuiButton binding; canonical OpenAllayButton remains the only button paint/theme owner. */
public abstract class GuideNativeButton extends GuiButton implements GuideWidgetInput {
    public boolean active = true;
    private boolean guideFocused;
    private boolean guideHovered;
    private ITextComponent message;
    private GuideTooltip tooltip;
    private final Consumer<OpenAllayButton> press;
    protected GuideNativeButton(int x, int y, int width, int height, ITextComponent title,
            Consumer<OpenAllayButton> press, GuideButtonNarration narration) {
        super(0, x, y, width, height, title.getFormattedText());
        this.message = Objects.requireNonNull(title, "title");
        this.press = Objects.requireNonNull(press, "press");
        Objects.requireNonNull(narration, "narration");
    }
    public final int getX() { return x; }
    public final int getY() { return y; }
    public final void setX(int value) { x = value; }
    public final void setY(int value) { y = value; }
    public final int getWidth() { return width; }
    public final int getHeight() { return height; }
    public final void setHeight(int value) { height = value; }
    public final ITextComponent getMessage() { return message; }
    public final void setMessage(ITextComponent message) {
        this.message = Objects.requireNonNull(message, "message");
        displayString = message.getFormattedText();
    }
    public final boolean isFocused() { return guideFocused; }
    public final boolean isGuideHovered() { return guideHovered; }
    public final void setTooltip(GuideTooltip tooltip) { this.tooltip = tooltip; }
    public final void onPress() { press.accept((OpenAllayButton) this); }
    @Override public final void drawButton(Minecraft client, int mouseX, int mouseY, float delta) {
        enabled = active;
        if (!visible) return;
        guideHovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        GuideGraphics graphics = GuideGraphics.wrap();
        graphics.paint(() -> {
            paintGuideButton(graphics, mouseX, mouseY, delta);
            if (tooltip != null && guideHovered) graphics.setTooltipForNextFrame(tooltip.text(), mouseX, mouseY);
        });
    }
    @Override public final boolean mousePressed(Minecraft client, int mouseX, int mouseY) {
        enabled = active;
        return super.mousePressed(client, mouseX, mouseY);
    }
    @Override public final boolean guideKeyPressed(GuideInputKey event) {
        if (!active || !visible || !guideFocused || !event.isConfirmation()) return false;
        onPress();
        return true;
    }
    @Override public final void guideSetFocused(boolean focused) { guideFocused = focused; }
    @Override public final boolean guideIsFocused() { return guideFocused; }
    protected abstract void paintGuideButton(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    protected final void paintGuideButtonLabel(GuideGraphics graphics, ITextComponent label, int padding) {
        var font = Minecraft.getMinecraft().fontRenderer;
        String text = font.trimStringToWidth(label.getFormattedText(), Math.max(1, width - padding * 2));
        graphics.text(font, text, x + (width - font.getStringWidth(text)) / 2,
                y + (height - font.FONT_HEIGHT) / 2, 0xFFFFFFFF, false);
    }
}
