package dev.openallay.skill;

@dev.openallay.value.ValueType(SkillDiagnostic.ValueSchemaProvider.class)
public final class SkillDiagnostic {
    private final String code;
    private final String message;
    private final String provenance;
    public SkillDiagnostic(String code, String message, String provenance) {
        this.code = code;
        this.message = message;
        this.provenance = provenance;
    }
    public String code() { return code; }
    public String message() { return message; }
    public String provenance() { return provenance; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SkillDiagnostic)) return false;
        SkillDiagnostic that = (SkillDiagnostic) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message) && java.util.Objects.equals(provenance, that.provenance);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        return hash;
    }
    @Override public String toString() { return "SkillDiagnostic[code=" + code + ", message=" + message + ", provenance=" + provenance + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SkillDiagnostic> schema() {
            return new dev.openallay.value.ValueSchema<>(SkillDiagnostic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SkillDiagnostic>>asList(new dev.openallay.value.ValueSchema.Component<>(SkillDiagnostic.class, "code", SkillDiagnostic::code), new dev.openallay.value.ValueSchema.Component<>(SkillDiagnostic.class, "message", SkillDiagnostic::message), new dev.openallay.value.ValueSchema.Component<>(SkillDiagnostic.class, "provenance", SkillDiagnostic::provenance)), arguments -> new SkillDiagnostic((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
