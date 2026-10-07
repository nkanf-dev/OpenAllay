package dev.openallay.script.result;

/** Java-side shape metadata. It is never injected into script-visible or canonical JSON data. */
@dev.openallay.value.ValueType(JavascriptResultShape.ValueSchemaProvider.class)
public final class JavascriptResultShape {
    private final JavascriptSemanticKind kind;
    private final boolean trusted;
    private final String declaredType;
    public JavascriptResultShape(JavascriptSemanticKind kind, boolean trusted, String declaredType) {

        java.util.Objects.requireNonNull(kind, "kind");
        declaredType = declaredType == null ? "" : declaredType;
        if (trusted && kind != JavascriptSemanticKind.RECIPE
                && kind != JavascriptSemanticKind.ITEM) {
            throw new IllegalArgumentException(
                    "only closed recipe and item host values can be trusted");
        }

        this.kind = kind;
        this.trusted = trusted;
        this.declaredType = declaredType;
    }
    public JavascriptSemanticKind kind() { return kind; }
    public boolean trusted() { return trusted; }
    public String declaredType() { return declaredType; }
public static JavascriptResultShape ordinary(JavascriptSemanticKind kind) {
        return new JavascriptResultShape(kind, false, "");
    }
public static JavascriptResultShape trusted(
            JavascriptSemanticKind kind, Class<?> declaredType) {
        return new JavascriptResultShape(kind, true, declaredType.getName());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavascriptResultShape)) return false;
        JavascriptResultShape that = (JavascriptResultShape) other;
        return java.util.Objects.equals(kind, that.kind) && trusted == that.trusted && java.util.Objects.equals(declaredType, that.declaredType);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + Boolean.hashCode(trusted);
        hash = 31 * hash + java.util.Objects.hashCode(declaredType);
        return hash;
    }
    @Override public String toString() { return "JavascriptResultShape[kind=" + kind + ", trusted=" + trusted + ", declaredType=" + declaredType + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<JavascriptResultShape> schema() {
            return new dev.openallay.value.ValueSchema<>(JavascriptResultShape.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<JavascriptResultShape>>asList(new dev.openallay.value.ValueSchema.Component<>(JavascriptResultShape.class, "kind", JavascriptResultShape::kind), new dev.openallay.value.ValueSchema.Component<>(JavascriptResultShape.class, "trusted", JavascriptResultShape::trusted), new dev.openallay.value.ValueSchema.Component<>(JavascriptResultShape.class, "declaredType", JavascriptResultShape::declaredType)), arguments -> new JavascriptResultShape((JavascriptSemanticKind) arguments[0], (Boolean) arguments[1], (String) arguments[2]));
        }
    }
}
