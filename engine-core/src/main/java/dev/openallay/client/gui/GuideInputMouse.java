package dev.openallay.client.gui;

/** Native pointer payload. Button semantics belong to the native input binding. */
@dev.openallay.value.ValueType(GuideInputMouse.ValueSchemaProvider.class)
public final class GuideInputMouse {
    private final double x;
    private final double y;
    private final int button;
    private final int modifiers;
    private final boolean leftClick;
    public GuideInputMouse(double x, double y, int button, int modifiers, boolean leftClick) {
        this.x = x;
        this.y = y;
        this.button = button;
        this.modifiers = modifiers;
        this.leftClick = leftClick;
    }
    public double x() { return x; }
    public double y() { return y; }
    public int button() { return button; }
    public int modifiers() { return modifiers; }
    public boolean leftClick() { return leftClick; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideInputMouse)) return false;
        GuideInputMouse that = (GuideInputMouse) other;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0 && button == that.button && modifiers == that.modifiers && leftClick == that.leftClick;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Double.hashCode(x);
        hash = 31 * hash + Double.hashCode(y);
        hash = 31 * hash + Integer.hashCode(button);
        hash = 31 * hash + Integer.hashCode(modifiers);
        hash = 31 * hash + Boolean.hashCode(leftClick);
        return hash;
    }
    @Override public String toString() { return "GuideInputMouse[x=" + x + ", y=" + y + ", button=" + button + ", modifiers=" + modifiers + ", leftClick=" + leftClick + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideInputMouse> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideInputMouse.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideInputMouse>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideInputMouse.class, "x", GuideInputMouse::x), new dev.openallay.value.ValueSchema.Component<>(GuideInputMouse.class, "y", GuideInputMouse::y), new dev.openallay.value.ValueSchema.Component<>(GuideInputMouse.class, "button", GuideInputMouse::button), new dev.openallay.value.ValueSchema.Component<>(GuideInputMouse.class, "modifiers", GuideInputMouse::modifiers), new dev.openallay.value.ValueSchema.Component<>(GuideInputMouse.class, "leftClick", GuideInputMouse::leftClick)), arguments -> new GuideInputMouse((Double) arguments[0], (Double) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Boolean) arguments[4]));
        }
    }
}
