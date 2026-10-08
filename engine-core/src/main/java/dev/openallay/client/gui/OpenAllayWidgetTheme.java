package dev.openallay.client.gui;

/** Shared hard-edged palette for OpenAllay's native Minecraft widgets. */
public final class OpenAllayWidgetTheme {
    public static final int CHARCOAL = 0xFF181B22;
    public static final int CHARCOAL_RAISED = 0xFF242933;
    public static final int CHARCOAL_HOVERED = 0xFF2B3B3B;
    public static final int CHARCOAL_DISABLED = 0xFF202329;
    public static final int SLATE_BORDER = 0xFF4A5561;
    public static final int SLATE_DISABLED = 0xFF343A42;
    public static final int MINT = 0xFF72D5C4;
    public static final int MINT_DARK = 0xFF355F59;
    public static final int AMBER = 0xFFF0B85B;
    public static final int WHITE = 0xFFE8EDF2;
    public static final int MUTED = 0xFF7F8994;

    public static final int PANEL = CHARCOAL;
    public static final int PANEL_ALT = CHARCOAL_RAISED;
    public static final int TEXT = WHITE;
    public static final int MUTED_READABLE = 0xFFA9B3BE;
    public static final int ERROR = 0xFFFF7D7D;
    public static final int SUCCESS = MINT;
    public static final int WARNING = AMBER;
    public static final int INFO = 0xFF8BBCEB;

    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 12;
    public static final int SPACE_LG = 16;
    public static final int SPACE_XL = 24;
    public static final int LINE_HEIGHT = 12;

    private OpenAllayWidgetTheme() {}

    public static ButtonVisualState buttonState(
            boolean active,
            boolean hovered,
            boolean focused,
            boolean selected) {
        if (selected) {
            return ButtonVisualState.SELECTED;
        }
        if (!active) {
            return ButtonVisualState.DISABLED;
        }
        if (focused) {
            return ButtonVisualState.FOCUSED;
        }
        if (hovered) {
            return ButtonVisualState.HOVERED;
        }
        return ButtonVisualState.IDLE;
    }

    public static ButtonColors buttonColors(ButtonVisualState state) {
        {
dev.openallay.client.gui.OpenAllayWidgetTheme.ButtonColors $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((state)) {
case IDLE:
{
$oaSwitch0_exit_result = new ButtonColors(
                    CHARCOAL_RAISED, SLATE_BORDER, CHARCOAL, WHITE, SLATE_BORDER); break $oaSwitch0_exit;
}
case HOVERED:
{
$oaSwitch0_exit_result = new ButtonColors(
                    CHARCOAL_HOVERED, MINT, CHARCOAL, WHITE, MINT); break $oaSwitch0_exit;
}
case FOCUSED:
{
$oaSwitch0_exit_result = new ButtonColors(
                    CHARCOAL_HOVERED, AMBER, CHARCOAL, WHITE, AMBER); break $oaSwitch0_exit;
}
case SELECTED:
{
$oaSwitch0_exit_result = new ButtonColors(
                    MINT_DARK, MINT, CHARCOAL, WHITE, MINT); break $oaSwitch0_exit;
}
case DISABLED:
{
$oaSwitch0_exit_result = new ButtonColors(
                    CHARCOAL_DISABLED, SLATE_DISABLED, CHARCOAL, MUTED, SLATE_DISABLED); break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
    }

    public enum ButtonVisualState {
        IDLE,
        HOVERED,
        FOCUSED,
        SELECTED,
        DISABLED
    }

    @dev.openallay.value.ValueType(ButtonColors.ValueSchemaProvider.class)
public static final class ButtonColors {
    private final int fill;
    private final int border;
    private final int shadow;
    private final int text;
    private final int marker;
    public ButtonColors(int fill, int border, int shadow, int text, int marker) {
        this.fill = fill;
        this.border = border;
        this.shadow = shadow;
        this.text = text;
        this.marker = marker;
    }
    public int fill() { return fill; }
    public int border() { return border; }
    public int shadow() { return shadow; }
    public int text() { return text; }
    public int marker() { return marker; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ButtonColors)) return false;
        ButtonColors that = (ButtonColors) other;
        return fill == that.fill && border == that.border && shadow == that.shadow && text == that.text && marker == that.marker;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(fill);
        hash = 31 * hash + Integer.hashCode(border);
        hash = 31 * hash + Integer.hashCode(shadow);
        hash = 31 * hash + Integer.hashCode(text);
        hash = 31 * hash + Integer.hashCode(marker);
        return hash;
    }
    @Override public String toString() { return "ButtonColors[fill=" + fill + ", border=" + border + ", shadow=" + shadow + ", text=" + text + ", marker=" + marker + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ButtonColors> schema() {
            return new dev.openallay.value.ValueSchema<>(ButtonColors.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ButtonColors>>asList(new dev.openallay.value.ValueSchema.Component<>(ButtonColors.class, "fill", ButtonColors::fill), new dev.openallay.value.ValueSchema.Component<>(ButtonColors.class, "border", ButtonColors::border), new dev.openallay.value.ValueSchema.Component<>(ButtonColors.class, "shadow", ButtonColors::shadow), new dev.openallay.value.ValueSchema.Component<>(ButtonColors.class, "text", ButtonColors::text), new dev.openallay.value.ValueSchema.Component<>(ButtonColors.class, "marker", ButtonColors::marker)), arguments -> new ButtonColors((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Integer) arguments[4]));
        }
    }
}
}
