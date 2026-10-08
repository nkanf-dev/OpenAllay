package dev.openallay.settings.requirement;

import dev.openallay.requirement.RequirementKind;
import java.util.Objects;

/** One exact, user-confirmed setting change; never a dependency closure. */
@dev.openallay.value.ValueType(RequirementChange.ValueSchemaProvider.class)
public final class RequirementChange {
    private final RequirementKind kind;
    private final String id;
    private final boolean unrestrictedConsentRequired;
    public RequirementChange(RequirementKind kind, String id, boolean unrestrictedConsentRequired) {

        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");

        this.kind = kind;
        this.id = id;
        this.unrestrictedConsentRequired = unrestrictedConsentRequired;
    }
    public RequirementKind kind() { return kind; }
    public String id() { return id; }
    public boolean unrestrictedConsentRequired() { return unrestrictedConsentRequired; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequirementChange)) return false;
        RequirementChange that = (RequirementChange) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(id, that.id) && unrestrictedConsentRequired == that.unrestrictedConsentRequired;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + Boolean.hashCode(unrestrictedConsentRequired);
        return hash;
    }
    @Override public String toString() { return "RequirementChange[kind=" + kind + ", id=" + id + ", unrestrictedConsentRequired=" + unrestrictedConsentRequired + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequirementChange> schema() {
            return new dev.openallay.value.ValueSchema<>(RequirementChange.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequirementChange>>asList(new dev.openallay.value.ValueSchema.Component<>(RequirementChange.class, "kind", RequirementChange::kind), new dev.openallay.value.ValueSchema.Component<>(RequirementChange.class, "id", RequirementChange::id), new dev.openallay.value.ValueSchema.Component<>(RequirementChange.class, "unrestrictedConsentRequired", RequirementChange::unrestrictedConsentRequired)), arguments -> new RequirementChange((RequirementKind) arguments[0], (String) arguments[1], (Boolean) arguments[2]));
        }
    }
}
