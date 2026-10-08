package dev.openallay.client.gui.nativeview;

/** Redacted, presentation-scoped provider diagnostic. */
@dev.openallay.value.ValueType(NativeDomainViewDiagnostic.ValueSchemaProvider.class)
public final class NativeDomainViewDiagnostic {
    private final String stableId;
    private final String providerId;
    private final String code;
    public NativeDomainViewDiagnostic(String stableId, String providerId, String code) {

        if (stableId == null || dev.openallay.util.Java8Strings.isBlank(stableId)
                || providerId == null || dev.openallay.util.Java8Strings.isBlank(providerId)
                || code == null || !code.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("native view diagnostic is invalid");
        }

        this.stableId = stableId;
        this.providerId = providerId;
        this.code = code;
    }
    public String stableId() { return stableId; }
    public String providerId() { return providerId; }
    public String code() { return code; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NativeDomainViewDiagnostic)) return false;
        NativeDomainViewDiagnostic that = (NativeDomainViewDiagnostic) other;
        return java.util.Objects.equals(stableId, that.stableId) && java.util.Objects.equals(providerId, that.providerId) && java.util.Objects.equals(code, that.code);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(stableId);
        hash = 31 * hash + java.util.Objects.hashCode(providerId);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        return hash;
    }
    @Override public String toString() { return "NativeDomainViewDiagnostic[stableId=" + stableId + ", providerId=" + providerId + ", code=" + code + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<NativeDomainViewDiagnostic> schema() {
            return new dev.openallay.value.ValueSchema<>(NativeDomainViewDiagnostic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<NativeDomainViewDiagnostic>>asList(new dev.openallay.value.ValueSchema.Component<>(NativeDomainViewDiagnostic.class, "stableId", NativeDomainViewDiagnostic::stableId), new dev.openallay.value.ValueSchema.Component<>(NativeDomainViewDiagnostic.class, "providerId", NativeDomainViewDiagnostic::providerId), new dev.openallay.value.ValueSchema.Component<>(NativeDomainViewDiagnostic.class, "code", NativeDomainViewDiagnostic::code)), arguments -> new NativeDomainViewDiagnostic((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
