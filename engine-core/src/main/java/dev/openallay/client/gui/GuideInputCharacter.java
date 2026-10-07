package dev.openallay.client.gui;

/** Native text payload. Text editing and IME ownership remain with native widgets. */
@dev.openallay.value.ValueType(GuideInputCharacter.ValueSchemaProvider.class)
public final class GuideInputCharacter {
    private final int codePoint;
    private final int modifiers;
    public GuideInputCharacter(int codePoint, int modifiers) {
        this.codePoint = codePoint;
        this.modifiers = modifiers;
    }
    public int codePoint() { return codePoint; }
    public int modifiers() { return modifiers; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideInputCharacter)) return false;
        GuideInputCharacter that = (GuideInputCharacter) other;
        return codePoint == that.codePoint && modifiers == that.modifiers;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(codePoint);
        hash = 31 * hash + Integer.hashCode(modifiers);
        return hash;
    }
    @Override public String toString() { return "GuideInputCharacter[codePoint=" + codePoint + ", modifiers=" + modifiers + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideInputCharacter> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideInputCharacter.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideInputCharacter>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideInputCharacter.class, "codePoint", GuideInputCharacter::codePoint), new dev.openallay.value.ValueSchema.Component<>(GuideInputCharacter.class, "modifiers", GuideInputCharacter::modifiers)), arguments -> new GuideInputCharacter((Integer) arguments[0], (Integer) arguments[1]));
        }
    }
}
