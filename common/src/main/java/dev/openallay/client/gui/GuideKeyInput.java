package dev.openallay.client.gui;


/** Translate once at screen entry. Minecraft retains ownership of text, shortcuts and IME. */
@dev.openallay.value.ValueType(GuideKeyInput.ValueSchemaProvider.class)
public final class GuideKeyInput {
    private final GuideKeyIntent intent;
    private final boolean confirmation;
    private final boolean shift;
    private final boolean control;
    private final boolean paste;
    public GuideKeyInput(GuideKeyIntent intent, boolean confirmation, boolean shift, boolean control, boolean paste) {
        this.intent = intent;
        this.confirmation = confirmation;
        this.shift = shift;
        this.control = control;
        this.paste = paste;
    }
    public GuideKeyIntent intent() { return intent; }
    public boolean confirmation() { return confirmation; }
    public boolean shift() { return shift; }
    public boolean control() { return control; }
    public boolean paste() { return paste; }
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideKeyInput)) return false;
        GuideKeyInput that = (GuideKeyInput) other;
        return java.util.Objects.equals(intent, that.intent) && confirmation == that.confirmation && shift == that.shift && control == that.control && paste == that.paste;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(intent);
        hash = 31 * hash + Boolean.hashCode(confirmation);
        hash = 31 * hash + Boolean.hashCode(shift);
        hash = 31 * hash + Boolean.hashCode(control);
        hash = 31 * hash + Boolean.hashCode(paste);
        return hash;
    }
    @Override public String toString() { return "GuideKeyInput[intent=" + intent + ", confirmation=" + confirmation + ", shift=" + shift + ", control=" + control + ", paste=" + paste + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideKeyInput> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideKeyInput.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideKeyInput>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideKeyInput.class, "intent", GuideKeyInput::intent), new dev.openallay.value.ValueSchema.Component<>(GuideKeyInput.class, "confirmation", GuideKeyInput::confirmation), new dev.openallay.value.ValueSchema.Component<>(GuideKeyInput.class, "shift", GuideKeyInput::shift), new dev.openallay.value.ValueSchema.Component<>(GuideKeyInput.class, "control", GuideKeyInput::control), new dev.openallay.value.ValueSchema.Component<>(GuideKeyInput.class, "paste", GuideKeyInput::paste)), arguments -> new GuideKeyInput((GuideKeyIntent) arguments[0], (Boolean) arguments[1], (Boolean) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4]));
        }
    }
}
