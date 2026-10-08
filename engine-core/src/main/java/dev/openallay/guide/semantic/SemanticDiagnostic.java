package dev.openallay.guide.semantic;

/** Redacted parser diagnostic; it deliberately carries no source/provider payload. */
@dev.openallay.value.ValueType(SemanticDiagnostic.ValueSchemaProvider.class)
public final class SemanticDiagnostic {
    private final String code;
    private final String nodeId;
    public SemanticDiagnostic(String code, String nodeId) {

        if (code == null || !code.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("semantic diagnostic code is invalid");
        }
        SemanticIds.require(nodeId);

        this.code = code;
        this.nodeId = nodeId;
    }
    public String code() { return code; }
    public String nodeId() { return nodeId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SemanticDiagnostic)) return false;
        SemanticDiagnostic that = (SemanticDiagnostic) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(nodeId, that.nodeId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        return hash;
    }
    @Override public String toString() { return "SemanticDiagnostic[code=" + code + ", nodeId=" + nodeId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SemanticDiagnostic> schema() {
            return new dev.openallay.value.ValueSchema<>(SemanticDiagnostic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SemanticDiagnostic>>asList(new dev.openallay.value.ValueSchema.Component<>(SemanticDiagnostic.class, "code", SemanticDiagnostic::code), new dev.openallay.value.ValueSchema.Component<>(SemanticDiagnostic.class, "nodeId", SemanticDiagnostic::nodeId)), arguments -> new SemanticDiagnostic((String) arguments[0], (String) arguments[1]));
        }
    }
}
