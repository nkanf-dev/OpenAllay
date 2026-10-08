package dev.openallay.script.result;

/**
 * Closed declaration for an Extension-owned typed result view.
 *
 * <p>The renderer remains Java-owned; an Extension declaration can select only a supported
 * semantic kind and cannot inject scripts or callbacks into the player UI.
 */
public interface JavascriptResultViewProvider {
    String id();

    JavascriptSemanticKind kind();

    String summary();

    @dev.openallay.value.ValueType(Declaration.ValueSchemaProvider.class)
public static final class Declaration implements JavascriptResultViewProvider {
    private final String id;
    private final JavascriptSemanticKind kind;
    private final String summary;
    public Declaration(String id, JavascriptSemanticKind kind, String summary) {

            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid result view ID: " + id);
            }
            java.util.Objects.requireNonNull(kind, "kind");
            if (summary == null || dev.openallay.util.Java8Strings.isBlank(summary)) {
                throw new IllegalArgumentException("Result view summary must not be blank");
            }
            summary = dev.openallay.util.Java8Strings.strip(summary);

        this.id = id;
        this.kind = kind;
        this.summary = summary;
    }
    public String id() { return id; }
    public JavascriptSemanticKind kind() { return kind; }
    public String summary() { return summary; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Declaration)) return false;
        Declaration that = (Declaration) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(summary, that.summary);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        return hash;
    }
    @Override public String toString() { return "Declaration[id=" + id + ", kind=" + kind + ", summary=" + summary + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Declaration> schema() {
            return new dev.openallay.value.ValueSchema<>(Declaration.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Declaration>>asList(new dev.openallay.value.ValueSchema.Component<>(Declaration.class, "id", Declaration::id), new dev.openallay.value.ValueSchema.Component<>(Declaration.class, "kind", Declaration::kind), new dev.openallay.value.ValueSchema.Component<>(Declaration.class, "summary", Declaration::summary)), arguments -> new Declaration((String) arguments[0], (JavascriptSemanticKind) arguments[1], (String) arguments[2]));
        }
    }
}
}
