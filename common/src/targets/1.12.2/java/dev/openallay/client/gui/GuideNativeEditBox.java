package dev.openallay.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Actual 1.12.2 text widget. Vanilla retains selection, clipboard, validation and cursor ownership. */
public class GuideNativeEditBox extends GuiTextField implements GuideWidgetInput {
    public GuideNativeEditBox(int id, FontRenderer font, int x, int y, int width, int height) {
        super(id, font, x, y, width, height);
    }
    @Override public boolean guideKeyPressed(GuideInputKey event) {
        return super.textboxKeyTyped('\0', event.key());
    }
    @Override public boolean guideCharTyped(GuideInputCharacter event) {
        boolean handled = false;
        for (char character : Character.toChars(event.codePoint())) {
            handled |= super.textboxKeyTyped(character, Keyboard.KEY_NONE);
        }
        return handled;
    }
    @Override public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) {
        return super.mouseClicked((int) event.x(), (int) event.y(), event.button());
    }
    @Override public void guideSetFocused(boolean focused) { super.setFocused(focused); }
    @Override public boolean guideIsFocused() { return super.isFocused(); }
    public void tick() { super.updateCursorCounter(); }
    public void render() { super.drawTextBox(); }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int value) { x = value; }
    public void setY(int value) { y = value; }
    public String getValue() { return super.getText(); }
    public void setValue(String value) { super.setText(value); }
    public void setMaxLength(int length) { super.setMaxStringLength(length); }
    public void setEditable(boolean editable) { super.setEnabled(editable); }
    public void setBordered(boolean bordered) { super.setEnableBackgroundDrawing(bordered); }
}
