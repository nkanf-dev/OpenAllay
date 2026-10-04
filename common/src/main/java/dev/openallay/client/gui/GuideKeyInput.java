package dev.openallay.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

/** Translate once at screen entry. Minecraft retains ownership of text, shortcuts and IME. */
public record GuideKeyInput(GuideKeyIntent intent, boolean confirmation, boolean shift,
        boolean control, boolean paste) {
    public static GuideKeyInput from(KeyEvent event) {
        GuideKeyIntent intent = switch (event.key()) {
            case InputConstants.KEY_ESCAPE -> GuideKeyIntent.ESCAPE;
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> GuideKeyIntent.ENTER;
            case InputConstants.KEY_SPACE -> GuideKeyIntent.SPACE;
            case InputConstants.KEY_UP -> GuideKeyIntent.UP;
            case InputConstants.KEY_DOWN -> GuideKeyIntent.DOWN;
            case InputConstants.KEY_PAGEUP -> GuideKeyIntent.PAGE_UP;
            case InputConstants.KEY_PAGEDOWN -> GuideKeyIntent.PAGE_DOWN;
            case InputConstants.KEY_HOME -> GuideKeyIntent.HOME;
            case InputConstants.KEY_END -> GuideKeyIntent.END;
            case InputConstants.KEY_F6 -> GuideKeyIntent.NEXT_CONTENT;
            default -> GuideKeyIntent.OTHER;
        };
        return new GuideKeyInput(intent, event.isConfirmation(), event.hasShiftDown(),
                event.hasControlDownWithQuirk(), event.isPaste());
    }
}
