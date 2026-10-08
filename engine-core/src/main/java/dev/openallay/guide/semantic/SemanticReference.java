package dev.openallay.guide.semantic;

/** Validated semantic reference; grounded authority is explicit and immutable. */
@dev.openallay.value.ValueType(SemanticReference.ValueSchemaProvider.class)
public final class SemanticReference {
    private final SemanticReferenceKind kind;
    private final String target;
    private final String label;
    private final boolean grounded;
    private final String originInvocationId;
    public SemanticReference(SemanticReferenceKind kind, String target, String label, boolean grounded, String originInvocationId) {

        java.util.Objects.requireNonNull(kind, "kind");
        if (target == null || dev.openallay.util.Java8Strings.isBlank(target)) {
            throw new IllegalArgumentException("semantic reference target is required");
        }
        label = label == null ? "" : dev.openallay.util.Java8Strings.strip(label);
        if (!grounded && originInvocationId != null) {
            throw new IllegalArgumentException("ungrounded reference cannot name an invocation");
        }
        if (grounded && (originInvocationId == null || dev.openallay.util.Java8Strings.isBlank(originInvocationId))) {
            throw new IllegalArgumentException("grounded reference requires its invocation origin");
        }

        this.kind = kind;
        this.target = target;
        this.label = label;
        this.grounded = grounded;
        this.originInvocationId = originInvocationId;
    }
    public SemanticReferenceKind kind() { return kind; }
    public String target() { return target; }
    public String label() { return label; }
    public boolean grounded() { return grounded; }
    public String originInvocationId() { return originInvocationId; }
public String displayText() {
        return dev.openallay.util.Java8Strings.isBlank(label) ? target : label;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SemanticReference)) return false;
        SemanticReference that = (SemanticReference) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(target, that.target) && java.util.Objects.equals(label, that.label) && grounded == that.grounded && java.util.Objects.equals(originInvocationId, that.originInvocationId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(target);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        hash = 31 * hash + Boolean.hashCode(grounded);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        return hash;
    }
    @Override public String toString() { return "SemanticReference[kind=" + kind + ", target=" + target + ", label=" + label + ", grounded=" + grounded + ", originInvocationId=" + originInvocationId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SemanticReference> schema() {
            return new dev.openallay.value.ValueSchema<>(SemanticReference.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SemanticReference>>asList(new dev.openallay.value.ValueSchema.Component<>(SemanticReference.class, "kind", SemanticReference::kind), new dev.openallay.value.ValueSchema.Component<>(SemanticReference.class, "target", SemanticReference::target), new dev.openallay.value.ValueSchema.Component<>(SemanticReference.class, "label", SemanticReference::label), new dev.openallay.value.ValueSchema.Component<>(SemanticReference.class, "grounded", SemanticReference::grounded), new dev.openallay.value.ValueSchema.Component<>(SemanticReference.class, "originInvocationId", SemanticReference::originInvocationId)), arguments -> new SemanticReference((SemanticReferenceKind) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3], (String) arguments[4]));
        }
    }
}
