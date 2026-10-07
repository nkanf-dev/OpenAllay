package dev.openallay.client.gui;

/** Native key payload plus semantics frozen by the actual native input binding. */
@dev.openallay.value.ValueType(GuideInputKey.ValueSchemaProvider.class)
public final class GuideInputKey {
    private final int key;
    private final int scancode;
    private final int keycode;
    private final int modifiers;
    private final boolean isConfirmation;
    private final boolean hasShiftDown;
    private final boolean controlDown;
    private final boolean isPaste;
    private final boolean isCopy;
    private final boolean isCut;
    private final boolean isEscape;
    public GuideInputKey(int key, int scancode, int keycode, int modifiers, boolean isConfirmation, boolean hasShiftDown, boolean controlDown, boolean isPaste, boolean isCopy, boolean isCut, boolean isEscape) {
        this.key = key;
        this.scancode = scancode;
        this.keycode = keycode;
        this.modifiers = modifiers;
        this.isConfirmation = isConfirmation;
        this.hasShiftDown = hasShiftDown;
        this.controlDown = controlDown;
        this.isPaste = isPaste;
        this.isCopy = isCopy;
        this.isCut = isCut;
        this.isEscape = isEscape;
    }
    public int key() { return key; }
    public int scancode() { return scancode; }
    public int keycode() { return keycode; }
    public int modifiers() { return modifiers; }
    public boolean isConfirmation() { return isConfirmation; }
    public boolean hasShiftDown() { return hasShiftDown; }
    public boolean controlDown() { return controlDown; }
    public boolean isPaste() { return isPaste; }
    public boolean isCopy() { return isCopy; }
    public boolean isCut() { return isCut; }
    public boolean isEscape() { return isEscape; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideInputKey)) return false;
        GuideInputKey that = (GuideInputKey) other;
        return key == that.key && scancode == that.scancode && keycode == that.keycode && modifiers == that.modifiers && isConfirmation == that.isConfirmation && hasShiftDown == that.hasShiftDown && controlDown == that.controlDown && isPaste == that.isPaste && isCopy == that.isCopy && isCut == that.isCut && isEscape == that.isEscape;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(key);
        hash = 31 * hash + Integer.hashCode(scancode);
        hash = 31 * hash + Integer.hashCode(keycode);
        hash = 31 * hash + Integer.hashCode(modifiers);
        hash = 31 * hash + Boolean.hashCode(isConfirmation);
        hash = 31 * hash + Boolean.hashCode(hasShiftDown);
        hash = 31 * hash + Boolean.hashCode(controlDown);
        hash = 31 * hash + Boolean.hashCode(isPaste);
        hash = 31 * hash + Boolean.hashCode(isCopy);
        hash = 31 * hash + Boolean.hashCode(isCut);
        hash = 31 * hash + Boolean.hashCode(isEscape);
        return hash;
    }
    @Override public String toString() { return "GuideInputKey[key=" + key + ", scancode=" + scancode + ", keycode=" + keycode + ", modifiers=" + modifiers + ", isConfirmation=" + isConfirmation + ", hasShiftDown=" + hasShiftDown + ", controlDown=" + controlDown + ", isPaste=" + isPaste + ", isCopy=" + isCopy + ", isCut=" + isCut + ", isEscape=" + isEscape + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideInputKey> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideInputKey.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideInputKey>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "key", GuideInputKey::key), new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "scancode", GuideInputKey::scancode), new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "keycode", GuideInputKey::keycode), new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "modifiers", GuideInputKey::modifiers), new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "isConfirmation", GuideInputKey::isConfirmation), new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "hasShiftDown", GuideInputKey::hasShiftDown), new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "controlDown", GuideInputKey::controlDown), new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "isPaste", GuideInputKey::isPaste), new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "isCopy", GuideInputKey::isCopy), new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "isCut", GuideInputKey::isCut), new dev.openallay.value.ValueSchema.Component<>(GuideInputKey.class, "isEscape", GuideInputKey::isEscape)), arguments -> new GuideInputKey((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (Boolean) arguments[7], (Boolean) arguments[8], (Boolean) arguments[9], (Boolean) arguments[10]));
        }
    }
}
