package dev.openallay.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Minecraft 26.2 native keyboard and text-focus binding. */
public final class GuideNativeInput {
    private GuideNativeInput() {}

    public static InputConstants.Type keyboardType() { return InputConstants.Type.KEYSYM; }

    /** Synthetic native event for inert controls and probes, not evidence of OS dispatch. */
    public static KeyEvent keyEvent(int key, int modifiers) { return new KeyEvent(key, 0, modifiers); }

    public static net.minecraft.client.input.CharacterEvent characterEvent(int codePoint) { return new net.minecraft.client.input.CharacterEvent(codePoint); }

    public static boolean isLeftClick(MouseButtonEvent event) {
        return event.button() == InputConstants.MOUSE_BUTTON_LEFT;
    }

    public static void releaseTextFocus(GuiEventListener widget) { widget.setFocused(false); }
}
