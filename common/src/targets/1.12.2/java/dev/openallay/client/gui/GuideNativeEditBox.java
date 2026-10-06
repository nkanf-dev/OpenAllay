package dev.openallay.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Actual 1.12.2 text widget. Vanilla retains selection, clipboard, validation and cursor ownership. */
public class GuideNativeEditBox extends GuiTextField implements GuideWidgetInput, GuideWidget {
    public boolean active = true;
    public boolean visible = true;
    private boolean editable = true;
    private java.util.function.Consumer<String> responder = ignored -> {};
    private java.util.function.BiFunction<String, Integer, GuideTextLine> formatter;
    private final FontRenderer guideFont;
    private net.minecraft.util.text.ITextComponent message = new net.minecraft.util.text.TextComponentString("");
    private net.minecraft.util.text.ITextComponent hint;
    public GuideNativeEditBox(FontRenderer font, int x, int y, int width, int height,
            net.minecraft.util.text.ITextComponent title) {
        this(0, font, x, y, width, height);
        message = java.util.Objects.requireNonNull(title, "title");
    }
    public GuideNativeEditBox(int id, FontRenderer font, int x, int y, int width, int height) {
        super(id, font, x, y, width, height);
        guideFont = font;
    }
    @Override public boolean guideKeyPressed(GuideInputKey event) {
        if (!active || !visible) return false;
        String previous = super.getText();
        super.setEnabled(editable);
        boolean handled = super.textboxKeyTyped('\0', event.key());
        notifyChanged(previous);
        return handled;
    }
    @Override public boolean guideCharTyped(GuideInputCharacter event) {
        if (!active || !visible) return false;
        String previous = super.getText();
        super.setEnabled(editable);
        boolean handled = false;
        for (char character : Character.toChars(event.codePoint())) {
            handled |= super.textboxKeyTyped(character, Keyboard.KEY_NONE);
        }
        notifyChanged(previous);
        return handled;
    }
    private void notifyChanged(String previous) { if (!super.getText().equals(previous)) responder.accept(super.getText()); }
    @Override public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) {
        if (!active || !visible) return false;
        return super.mouseClicked((int) event.x(), (int) event.y(), event.button());
    }
    @Override public void guideSetFocused(boolean focused) { super.setFocused(focused); }
    @Override public boolean guideIsFocused() { return super.isFocused(); }
    public void tick() { super.updateCursorCounter(); }
    public void render() { drawTextBox(); }
    @Override public void drawTextBox() {
        super.setEnabled(active && editable);
        super.setVisible(visible);
        if (!visible) return;
        if (formatter == null) super.drawTextBox();
        else {
            GuideTextFieldDrawAccess access = (GuideTextFieldDrawAccess) this;
            String original = access.openallay$drawText();
            String projected = GuideNativeFont.nativeLine(formatter.apply(original, 0));
            if (projected.length() != original.length()) {
                throw new IllegalArgumentException("Native text-field draw projection must retain cursor indices");
            }
            access.openallay$drawText(projected);
            try { super.drawTextBox(); }
            finally { access.openallay$drawText(original); }
        }
        if (hint != null && super.getText().isEmpty() && !super.isFocused()) {
            int padding = super.getEnableBackgroundDrawing() ? 4 : 0;
            guideFont.drawString(guideFont.trimStringToWidth(hint.getFormattedText(), super.getWidth()),
                    x + padding, y + (height - guideFont.FONT_HEIGHT) / 2, 0xFF707070, false);
        }
    }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int value) { x = value; }
    public void setY(int value) { y = value; }
    public int getHeight() { return height; }
    public net.minecraft.util.text.ITextComponent getMessage() { return message; }
    public Class<?> guideNativeType() { return getClass(); }
    public boolean guideActive() { return active; }
    public void guideActive(boolean active) { this.active = active; }
    public boolean guideVisible() { return visible; }
    public void guideVisible(boolean visible) { this.visible = visible; }
    public String getValue() { return super.getText(); }
    public void setValue(String value) {
        String previous = super.getText();
        super.setText(value);
        notifyChanged(previous);
    }
    public void setResponder(java.util.function.Consumer<String> listener) { responder = java.util.Objects.requireNonNull(listener, "listener"); }
    public void setHint(net.minecraft.util.text.ITextComponent hint) { this.hint = hint; }
    protected final void formatGuideText(java.util.function.BiFunction<String, Integer, GuideTextLine> formatter) {
        this.formatter = java.util.Objects.requireNonNull(formatter, "formatter");
    }
    public void setMaxLength(int length) { super.setMaxStringLength(length); }
    public void setEditable(boolean editable) { this.editable = editable; super.setEnabled(editable); }
    public void setBordered(boolean bordered) { super.setEnableBackgroundDrawing(bordered); }
    protected net.minecraft.util.text.ITextComponent guideNarrationMessage() { return getMessage(); }
}
