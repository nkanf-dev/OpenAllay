package dev.openallay.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.sdl.SDLKeyboard;

/** Minecraft 26.3 SDL scancodes, native keycodes and owner-scoped text focus. */
public final class GuideNativeInput {
    private GuideNativeInput() {}

    public static InputConstants.Type keyboardType() { return InputConstants.Type.KEYBOARD; }

    /** Keep both inspected native event fields; shortcuts use keycode, mappings use scancode. */
    public static KeyEvent keyEvent(int key, int modifiers) {
        return new KeyEvent(key, SDLKeyboard.SDL_GetKeyFromScancode(key, (short) modifiers, false), modifiers);
    }

    public static net.minecraft.client.input.CharacterEvent characterEvent(int codePoint) { return new net.minecraft.client.input.CharacterEvent(codePoint); }

    public static boolean isLeftClick(MouseButtonEvent event) {
        return event.button() == InputConstants.MOUSE_BUTTON_LEFT;
    }

    public static void releaseTextFocus(GuiEventListener widget) {
        // The widget starts/stops SDL text input through Minecraft.onTextInputFocusChange.
        widget.setFocused(false);
    }
}
