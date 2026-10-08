package dev.openallay.requirement;

import java.util.Objects;

/** One immutable, caller-projected current settings/catalog state. */
@dev.openallay.value.ValueType(RequirementAvailability.ValueSchemaProvider.class)
public final class RequirementAvailability {
    private final String name;
    private final RequirementStatus status;
    private final String detail;
    public RequirementAvailability(String name, RequirementStatus status, String detail) {

        if (name == null || dev.openallay.util.Java8Strings.isBlank(name)) {
            throw new IllegalArgumentException("Requirement name must not be blank");
        }
        Objects.requireNonNull(status, "status");
        detail = dev.openallay.util.Java8Objects.requireNonNullElse(detail, "");

        this.name = name;
        this.status = status;
        this.detail = detail;
    }
    public String name() { return name; }
    public RequirementStatus status() { return status; }
    public String detail() { return detail; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequirementAvailability)) return false;
        RequirementAvailability that = (RequirementAvailability) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(detail, that.detail);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(detail);
        return hash;
    }
    @Override public String toString() { return "RequirementAvailability[name=" + name + ", status=" + status + ", detail=" + detail + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequirementAvailability> schema() {
            return new dev.openallay.value.ValueSchema<>(RequirementAvailability.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequirementAvailability>>asList(new dev.openallay.value.ValueSchema.Component<>(RequirementAvailability.class, "name", RequirementAvailability::name), new dev.openallay.value.ValueSchema.Component<>(RequirementAvailability.class, "status", RequirementAvailability::status), new dev.openallay.value.ValueSchema.Component<>(RequirementAvailability.class, "detail", RequirementAvailability::detail)), arguments -> new RequirementAvailability((String) arguments[0], (RequirementStatus) arguments[1], (String) arguments[2]));
        }
    }
}
