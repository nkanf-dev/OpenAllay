package dev.openallay.script.result;

/** Java-side shape metadata. It is never injected into script-visible or canonical JSON data. */
public record JavascriptResultShape(
        JavascriptSemanticKind kind,
        boolean trusted,
        String declaredType) {
    public JavascriptResultShape {
        java.util.Objects.requireNonNull(kind, "kind");
        declaredType = declaredType == null ? "" : declaredType;
        if (trusted && kind != JavascriptSemanticKind.RECIPE
                && kind != JavascriptSemanticKind.ITEM) {
            throw new IllegalArgumentException(
                    "only closed recipe and item host values can be trusted");
        }
    }

    public static JavascriptResultShape ordinary(JavascriptSemanticKind kind) {
        return new JavascriptResultShape(kind, false, "");
    }

    public static JavascriptResultShape trusted(
            JavascriptSemanticKind kind, Class<?> declaredType) {
        return new JavascriptResultShape(kind, true, declaredType.getName());
    }
}
