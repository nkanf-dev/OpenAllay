package dev.openallay.knowledge.online;

@dev.openallay.value.ValueType(OnlineKnowledgeDiagnostic.ValueSchemaProvider.class)
public final class OnlineKnowledgeDiagnostic {
    private final String sourceId;
    private final String code;
    private final String message;
    public OnlineKnowledgeDiagnostic(String sourceId, String code, String message) {

        if (sourceId == null || sourceId.isBlank()
                || code == null || code.isBlank()
                || message == null || message.isBlank()) {
            throw new IllegalArgumentException("invalid online knowledge diagnostic");
        }

        this.sourceId = sourceId;
        this.code = code;
        this.message = message;
    }
    public String sourceId() { return sourceId; }
    public String code() { return code; }
    public String message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OnlineKnowledgeDiagnostic)) return false;
        OnlineKnowledgeDiagnostic that = (OnlineKnowledgeDiagnostic) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "OnlineKnowledgeDiagnostic[sourceId=" + sourceId + ", code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<OnlineKnowledgeDiagnostic> schema() {
            return new dev.openallay.value.ValueSchema<>(OnlineKnowledgeDiagnostic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<OnlineKnowledgeDiagnostic>>asList(new dev.openallay.value.ValueSchema.Component<>(OnlineKnowledgeDiagnostic.class, "sourceId", OnlineKnowledgeDiagnostic::sourceId), new dev.openallay.value.ValueSchema.Component<>(OnlineKnowledgeDiagnostic.class, "code", OnlineKnowledgeDiagnostic::code), new dev.openallay.value.ValueSchema.Component<>(OnlineKnowledgeDiagnostic.class, "message", OnlineKnowledgeDiagnostic::message)), arguments -> new OnlineKnowledgeDiagnostic((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
