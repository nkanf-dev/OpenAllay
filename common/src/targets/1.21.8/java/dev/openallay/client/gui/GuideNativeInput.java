package dev.openallay.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.navigation.CommonInputs;

/** Primitive callback family. Native widgets retain keyboard, clipboard and IME ownership. */
public final class GuideNativeInput {
    private GuideNativeInput() {}
    public static String getClipboard() { return net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard(); }
    public static void setClipboard(String text) { net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text); }

    public static GuideWidgetInput widgetInput(GuiEventListener widget) {
        return new GuideNativeWidgetInput(widget);
    }

    public static InputConstants.Type keyboardType() { return InputConstants.Type.KEYSYM; }
    /** Inert/probe callback payload, not evidence of physical OS dispatch. */
    public static GuideInputKey keyEvent(int key, int modifiers) { return capture(key, 0, modifiers); }
    public static GuideInputKey capture(int key, int scancode, int modifiers) {
        return new GuideInputKey(key, scancode, key, modifiers,
                CommonInputs.selected(key),
                Screen.hasShiftDown(), Screen.hasControlDown(), Screen.isPaste(key),
                Screen.isCopy(key), Screen.isCut(key), key == dev.openallay.client.gui.GuideInputCodes.KEY_ESCAPE);
    }
    public static GuideInputCharacter characterEvent(int codePoint) { return new GuideInputCharacter(codePoint, 0); }
    public static GuideInputCharacter capture(char character, int modifiers) { return new GuideInputCharacter(character, modifiers); }
    public static GuideInputMouse mouseEvent(double x, double y, int button, int modifiers) {
        return new GuideInputMouse(x, y, button, modifiers, button == dev.openallay.client.gui.GuideInputCodes.MOUSE_BUTTON_LEFT);
    }
    public static GuideInputMouse capture(double x, double y, int button) { return mouseEvent(x, y, button, 0); }
    public static boolean controlDown(GuideInputKey event) { return event.controlDown(); }
    public static boolean isLeftClick(GuideInputMouse event) { return event.leftClick(); }
    public static boolean matches(KeyMapping mapping, GuideInputKey event) { return mapping.matches(event.key(), event.scancode()); }
    public static boolean keyPressed(GuiEventListener widget, GuideInputKey event) { return widget.keyPressed(event.key(), event.scancode(), event.modifiers()); }
    public static boolean keyReleased(GuiEventListener widget, GuideInputKey event) { return widget.keyReleased(event.key(), event.scancode(), event.modifiers()); }
    public static boolean charTyped(GuiEventListener widget, GuideInputCharacter event) {
        boolean handled = false;
        for (char character : Character.toChars(event.codePoint())) handled |= widget.charTyped(character, event.modifiers());
        return handled;
    }
    public static boolean mouseClicked(GuiEventListener widget, GuideInputMouse event, boolean doubleClick) { return widget.mouseClicked(event.x(), event.y(), event.button()); }
    public static boolean mouseDragged(GuiEventListener widget, GuideInputMouse event, double dx, double dy) { return widget.mouseDragged(event.x(), event.y(), event.button(), dx, dy); }
    public static boolean mouseReleased(GuiEventListener widget, GuideInputMouse event) { return widget.mouseReleased(event.x(), event.y(), event.button()); }
    public static void press(Button button, GuideInputKey event) { button.onPress(); }
    public static void click(AbstractSliderButton slider, GuideInputMouse event, boolean doubleClick) { slider.onClick(event.x(), event.y()); }
    public static void release(AbstractSliderButton slider, GuideInputMouse event) { slider.onRelease(event.x(), event.y()); }
    public static String keyEventType() { return "GuiEventListener.keyPressed(int,int,int)"; }
    public static String mouseEventType() { return "GuiEventListener.mouseClicked(double,double,int)"; }
    public static String characterEventType() { return "GuiEventListener.charTyped(char,int)"; }
    public static void releaseTextFocus(GuiEventListener widget) { widget.setFocused(false); }
}
