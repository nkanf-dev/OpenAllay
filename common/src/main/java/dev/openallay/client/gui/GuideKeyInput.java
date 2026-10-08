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
        dev.openallay.client.gui.GuideKeyIntent $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((event.key())) {
case dev.openallay.client.gui.GuideInputCodes.KEY_ESCAPE:
{
$oaSwitch0_exit_result = GuideKeyIntent.ESCAPE; break $oaSwitch0_exit;
}
case dev.openallay.client.gui.GuideInputCodes.KEY_RETURN:
case dev.openallay.client.gui.GuideInputCodes.KEY_NUMPADENTER:
{
$oaSwitch0_exit_result = GuideKeyIntent.ENTER; break $oaSwitch0_exit;
}
case dev.openallay.client.gui.GuideInputCodes.KEY_SPACE:
{
$oaSwitch0_exit_result = GuideKeyIntent.SPACE; break $oaSwitch0_exit;
}
case dev.openallay.client.gui.GuideInputCodes.KEY_UP:
{
$oaSwitch0_exit_result = GuideKeyIntent.UP; break $oaSwitch0_exit;
}
case dev.openallay.client.gui.GuideInputCodes.KEY_DOWN:
{
$oaSwitch0_exit_result = GuideKeyIntent.DOWN; break $oaSwitch0_exit;
}
case dev.openallay.client.gui.GuideInputCodes.KEY_PAGEUP:
{
$oaSwitch0_exit_result = GuideKeyIntent.PAGE_UP; break $oaSwitch0_exit;
}
case dev.openallay.client.gui.GuideInputCodes.KEY_PAGEDOWN:
{
$oaSwitch0_exit_result = GuideKeyIntent.PAGE_DOWN; break $oaSwitch0_exit;
}
case dev.openallay.client.gui.GuideInputCodes.KEY_HOME:
{
$oaSwitch0_exit_result = GuideKeyIntent.HOME; break $oaSwitch0_exit;
}
case dev.openallay.client.gui.GuideInputCodes.KEY_END:
{
$oaSwitch0_exit_result = GuideKeyIntent.END; break $oaSwitch0_exit;
}
case dev.openallay.client.gui.GuideInputCodes.KEY_F6:
{
$oaSwitch0_exit_result = GuideKeyIntent.NEXT_CONTENT; break $oaSwitch0_exit;
}
default:
{
$oaSwitch0_exit_result = GuideKeyIntent.OTHER; break $oaSwitch0_exit;
}
}
}
GuideKeyIntent intent = $oaSwitch0_exit_result;
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
