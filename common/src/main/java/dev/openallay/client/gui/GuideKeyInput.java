package dev.openallay.client.gui;


/** Translate once at screen entry. Minecraft retains ownership of text, shortcuts and IME. */
public record GuideKeyInput(GuideKeyIntent intent, boolean confirmation, boolean shift,
        boolean control, boolean paste) {
    public static GuideKeyInput from(GuideInputKey event) {
        GuideKeyIntent intent = switch (event.key()) {
            case dev.openallay.client.gui.GuideInputCodes.KEY_ESCAPE -> GuideKeyIntent.ESCAPE;
            case dev.openallay.client.gui.GuideInputCodes.KEY_RETURN, dev.openallay.client.gui.GuideInputCodes.KEY_NUMPADENTER -> GuideKeyIntent.ENTER;
            case dev.openallay.client.gui.GuideInputCodes.KEY_SPACE -> GuideKeyIntent.SPACE;
            case dev.openallay.client.gui.GuideInputCodes.KEY_UP -> GuideKeyIntent.UP;
            case dev.openallay.client.gui.GuideInputCodes.KEY_DOWN -> GuideKeyIntent.DOWN;
            case dev.openallay.client.gui.GuideInputCodes.KEY_PAGEUP -> GuideKeyIntent.PAGE_UP;
            case dev.openallay.client.gui.GuideInputCodes.KEY_PAGEDOWN -> GuideKeyIntent.PAGE_DOWN;
            case dev.openallay.client.gui.GuideInputCodes.KEY_HOME -> GuideKeyIntent.HOME;
            case dev.openallay.client.gui.GuideInputCodes.KEY_END -> GuideKeyIntent.END;
            case dev.openallay.client.gui.GuideInputCodes.KEY_F6 -> GuideKeyIntent.NEXT_CONTENT;
            default -> GuideKeyIntent.OTHER;
        };
        return new GuideKeyInput(intent, event.isConfirmation(), event.hasShiftDown(),
                event.controlDown(), event.isPaste());
    }
}
