package dev.openallay.client.gui;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** LWJGL 2 event semantics captured at the actual GuiScreen callback boundary. */
public final class GuideNativeInput {
    private GuideNativeInput() {}
    public static GuideWidgetInput widgetInput(GuideWidgetInput widget) {
        return java.util.Objects.requireNonNull(widget, "widget");
    }

    public static int modifiers() {
        return (GuiScreen.isShiftKeyDown() ? 1 : 0)
                | (GuiScreen.isCtrlKeyDown() ? 2 : 0)
                | (GuiScreen.isAltKeyDown() ? 4 : 0);
    }
    /** Probe payload only. Physical events arrive through GuiScreen.handleInput. */
    public static GuideInputKey keyEvent(int key, int modifiers) { return capture(key, 0, modifiers); }
    public static GuideInputKey capture(int key, int scancode, int modifiers) {
        return new GuideInputKey(key, scancode, key, modifiers,
                key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_SPACE,
                GuiScreen.isShiftKeyDown(), GuiScreen.isCtrlKeyDown(),
                GuiScreen.isKeyComboCtrlV(key), GuiScreen.isKeyComboCtrlC(key),
                GuiScreen.isKeyComboCtrlX(key), key == Keyboard.KEY_ESCAPE);
    }
    public static GuideInputCharacter characterEvent(int codePoint) { return new GuideInputCharacter(codePoint, 0); }
    public static GuideInputCharacter capture(char character, int modifiers) { return new GuideInputCharacter(character, modifiers); }
    public static GuideInputMouse mouseEvent(double x, double y, int button, int modifiers) {
        return new GuideInputMouse(x, y, button, modifiers, button == GuideInputCodes.MOUSE_BUTTON_LEFT);
    }
    public static GuideInputMouse capture(double x, double y, int button) { return mouseEvent(x, y, button, modifiers()); }
    public static boolean matches(net.minecraft.client.settings.KeyBinding mapping, GuideInputKey event) {
        return mapping.isActiveAndMatches(event.key());
    }
    public static boolean controlDown(GuideInputKey event) { return event.controlDown(); }
    public static boolean isLeftClick(GuideInputMouse event) { return event.leftClick(); }
    public static boolean keyPressed(GuideWidgetInput widget, GuideInputKey event) { return widget.guideKeyPressed(event); }
    public static boolean keyReleased(GuideWidgetInput widget, GuideInputKey event) { return widget.guideKeyReleased(event); }
    public static boolean charTyped(GuideWidgetInput widget, GuideInputCharacter event) { return widget.guideCharTyped(event); }
    public static boolean mouseClicked(GuideWidgetInput widget, GuideInputMouse event, boolean doubleClick) { return widget.guideMouseClicked(event, doubleClick); }
    public static boolean mouseDragged(GuideWidgetInput widget, GuideInputMouse event, double dx, double dy) { return widget.guideMouseDragged(event, dx, dy); }
    public static boolean mouseReleased(GuideWidgetInput widget, GuideInputMouse event) { return widget.guideMouseReleased(event); }
    public static void releaseTextFocus(GuideWidgetInput widget) { widget.guideSetFocused(false); }
    public static void press(GuideNativeButton button, GuideInputKey event) { button.onPress(); }
    public static String keyEventType() { return "GuiScreen.keyTyped(char,int)/Keyboard"; }
    public static String mouseEventType() { return "GuiScreen.mouseClicked(int,int,int)/Mouse"; }
    public static String characterEventType() { return "GuiScreen.keyTyped(char,int)"; }
}
