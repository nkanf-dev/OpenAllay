package dev.openallay.requirement;

import java.util.Objects;

@dev.openallay.value.ValueType(RequirementAssessment.ValueSchemaProvider.class)
public final class RequirementAssessment {
    private final RequirementKind kind;
    private final String id;
    private final String name;
    private final RequirementStatus status;
    private final String detail;
    public RequirementAssessment(RequirementKind kind, String id, String name, RequirementStatus status, String detail) {

        Objects.requireNonNull(kind, "kind");
        RequirementSet.requireId(id, kind);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Requirement name must not be blank");
        }
        Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNullElse(detail, "");

        this.kind = kind;
        this.id = id;
        this.name = name;
        this.status = status;
        this.detail = detail;
    }
    public RequirementKind kind() { return kind; }
    public String id() { return id; }
    public String name() { return name; }
    public RequirementStatus status() { return status; }
    public String detail() { return detail; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequirementAssessment)) return false;
        RequirementAssessment that = (RequirementAssessment) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(detail, that.detail);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(detail);
        return hash;
    }
    @Override public String toString() { return "RequirementAssessment[kind=" + kind + ", id=" + id + ", name=" + name + ", status=" + status + ", detail=" + detail + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequirementAssessment> schema() {
            return new dev.openallay.value.ValueSchema<>(RequirementAssessment.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequirementAssessment>>asList(new dev.openallay.value.ValueSchema.Component<>(RequirementAssessment.class, "kind", RequirementAssessment::kind), new dev.openallay.value.ValueSchema.Component<>(RequirementAssessment.class, "id", RequirementAssessment::id), new dev.openallay.value.ValueSchema.Component<>(RequirementAssessment.class, "name", RequirementAssessment::name), new dev.openallay.value.ValueSchema.Component<>(RequirementAssessment.class, "status", RequirementAssessment::status), new dev.openallay.value.ValueSchema.Component<>(RequirementAssessment.class, "detail", RequirementAssessment::detail)), arguments -> new RequirementAssessment((RequirementKind) arguments[0], (String) arguments[1], (String) arguments[2], (RequirementStatus) arguments[3], (String) arguments[4]));
        }
    }
}
