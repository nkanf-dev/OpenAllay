package dev.openallay.crafting;

import java.util.List;

@dev.openallay.value.ValueType(MissingRequirement.ValueSchemaProvider.class)
public final class MissingRequirement {
    private final String requirementKey;
    private final long required;
    private final long allocated;
    private final long missing;
    private final List<String> alternatives;
    public MissingRequirement(String requirementKey, long required, long allocated, long missing, List<String> alternatives) {

        if (requirementKey == null || requirementKey.isBlank()
                || required <= 0
                || allocated < 0
                || missing <= 0
                || allocated + missing != required) {
            throw new IllegalArgumentException("missing requirement counts are inconsistent");
        }
        alternatives = List.copyOf(alternatives);

        this.requirementKey = requirementKey;
        this.required = required;
        this.allocated = allocated;
        this.missing = missing;
        this.alternatives = alternatives;
    }
    public String requirementKey() { return requirementKey; }
    public long required() { return required; }
    public long allocated() { return allocated; }
    public long missing() { return missing; }
    public List<String> alternatives() { return alternatives; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MissingRequirement)) return false;
        MissingRequirement that = (MissingRequirement) other;
        return java.util.Objects.equals(requirementKey, that.requirementKey) && required == that.required && allocated == that.allocated && missing == that.missing && java.util.Objects.equals(alternatives, that.alternatives);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requirementKey);
        hash = 31 * hash + Long.hashCode(required);
        hash = 31 * hash + Long.hashCode(allocated);
        hash = 31 * hash + Long.hashCode(missing);
        hash = 31 * hash + java.util.Objects.hashCode(alternatives);
        return hash;
    }
    @Override public String toString() { return "MissingRequirement[requirementKey=" + requirementKey + ", required=" + required + ", allocated=" + allocated + ", missing=" + missing + ", alternatives=" + alternatives + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<MissingRequirement> schema() {
            return new dev.openallay.value.ValueSchema<>(MissingRequirement.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<MissingRequirement>>asList(new dev.openallay.value.ValueSchema.Component<>(MissingRequirement.class, "requirementKey", MissingRequirement::requirementKey), new dev.openallay.value.ValueSchema.Component<>(MissingRequirement.class, "required", MissingRequirement::required), new dev.openallay.value.ValueSchema.Component<>(MissingRequirement.class, "allocated", MissingRequirement::allocated), new dev.openallay.value.ValueSchema.Component<>(MissingRequirement.class, "missing", MissingRequirement::missing), new dev.openallay.value.ValueSchema.Component<>(MissingRequirement.class, "alternatives", MissingRequirement::alternatives)), arguments -> new MissingRequirement((String) arguments[0], (Long) arguments[1], (Long) arguments[2], (Long) arguments[3], (List) arguments[4]));
        }
    }
}
