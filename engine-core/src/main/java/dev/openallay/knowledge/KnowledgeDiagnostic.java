package dev.openallay.knowledge;

@dev.openallay.value.ValueType(KnowledgeDiagnostic.ValueSchemaProvider.class)
public final class KnowledgeDiagnostic {
    private final String sourceId;
    private final String code;
    private final String message;
    private final String provenance;
    public KnowledgeDiagnostic(String sourceId, String code, String message, String provenance) {
        this.sourceId = sourceId;
        this.code = code;
        this.message = message;
        this.provenance = provenance;
    }
    public String sourceId() { return sourceId; }
    public String code() { return code; }
    public String message() { return message; }
    public String provenance() { return provenance; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KnowledgeDiagnostic)) return false;
        KnowledgeDiagnostic that = (KnowledgeDiagnostic) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message) && java.util.Objects.equals(provenance, that.provenance);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        return hash;
    }
    @Override public String toString() { return "KnowledgeDiagnostic[sourceId=" + sourceId + ", code=" + code + ", message=" + message + ", provenance=" + provenance + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<KnowledgeDiagnostic> schema() {
            return new dev.openallay.value.ValueSchema<>(KnowledgeDiagnostic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<KnowledgeDiagnostic>>asList(new dev.openallay.value.ValueSchema.Component<>(KnowledgeDiagnostic.class, "sourceId", KnowledgeDiagnostic::sourceId), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDiagnostic.class, "code", KnowledgeDiagnostic::code), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDiagnostic.class, "message", KnowledgeDiagnostic::message), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDiagnostic.class, "provenance", KnowledgeDiagnostic::provenance)), arguments -> new KnowledgeDiagnostic((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}
