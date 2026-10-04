package dev.openallay.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.CharacterEvent;

/** Native input binding for 1.21.11; shared handlers never own Minecraft event types. */
public final class GuideNativeInput {
    private GuideNativeInput() {}
    public static InputConstants.Type keyboardType() { return InputConstants.Type.KEYSYM; }

    /** Inert/probe callback payload, not evidence of physical OS dispatch. */
    public static GuideInputKey keyEvent(int key, int modifiers) { return capture(new KeyEvent(key, 0, modifiers)); }
    public static GuideInputKey capture(KeyEvent event) {
        return new GuideInputKey(event.key(), event.scancode(), event.key(), event.modifiers(),
                event.isConfirmation(), event.hasShiftDown(), event.hasControlDownWithQuirk(),
                event.isPaste(), event.isCopy(), event.isCut(), event.key() == InputConstants.KEY_ESCAPE);
    }
    public static KeyEvent nativeKey(GuideInputKey event) {
        return new KeyEvent(event.key(), event.scancode(), event.modifiers());
    }
    public static GuideInputCharacter characterEvent(int codePoint) { return capture(new CharacterEvent(codePoint, 0)); }
    public static GuideInputCharacter capture(CharacterEvent event) { return new GuideInputCharacter(event.codepoint(), event.modifiers()); }
    public static CharacterEvent nativeCharacter(GuideInputCharacter event) { return new CharacterEvent(event.codePoint(), event.modifiers()); }

    public static GuideInputMouse mouseEvent(double x, double y, int button, int modifiers) {
        return capture(new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers)));
    }
    public static GuideInputMouse capture(MouseButtonEvent event) {
        return new GuideInputMouse(event.x(), event.y(), event.button(), event.modifiers(),
                event.button() == InputConstants.MOUSE_BUTTON_LEFT);
    }
    public static MouseButtonEvent nativeMouse(GuideInputMouse event) {
        return new MouseButtonEvent(event.x(), event.y(), new MouseButtonInfo(event.button(), event.modifiers()));
    }
    public static boolean controlDown(GuideInputKey event) { return event.controlDown(); }
    public static boolean isLeftClick(GuideInputMouse event) { return event.leftClick(); }
    public static boolean matches(KeyMapping mapping, GuideInputKey event) { return mapping.matches(nativeKey(event)); }
    public static boolean keyPressed(GuiEventListener widget, GuideInputKey event) { return widget.keyPressed(nativeKey(event)); }
    public static boolean keyReleased(GuiEventListener widget, GuideInputKey event) { return widget.keyReleased(nativeKey(event)); }
    public static boolean charTyped(GuiEventListener widget, GuideInputCharacter event) { return widget.charTyped(nativeCharacter(event)); }
    public static boolean mouseClicked(GuiEventListener widget, GuideInputMouse event, boolean doubleClick) { return widget.mouseClicked(nativeMouse(event), doubleClick); }
    public static boolean mouseDragged(GuiEventListener widget, GuideInputMouse event, double dx, double dy) { return widget.mouseDragged(nativeMouse(event), dx, dy); }
    public static boolean mouseReleased(GuiEventListener widget, GuideInputMouse event) { return widget.mouseReleased(nativeMouse(event)); }
    public static void press(Button button, GuideInputKey event) { button.onPress(nativeKey(event)); }
    public static void click(AbstractSliderButton slider, GuideInputMouse event, boolean doubleClick) { slider.onClick(nativeMouse(event), doubleClick); }
    public static void release(AbstractSliderButton slider, GuideInputMouse event) { slider.onRelease(nativeMouse(event)); }
    public static String keyEventType() { return KeyEvent.class.getName(); }
    public static String mouseEventType() { return MouseButtonEvent.class.getName(); }
    public static String characterEventType() { return CharacterEvent.class.getName(); }
    public static void releaseTextFocus(GuiEventListener widget) { widget.setFocused(false); }
}
