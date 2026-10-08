package dev.openallay.context;

import java.util.List;

@dev.openallay.value.ValueType(IngredientRequirementSnapshot.ValueSchemaProvider.class)
public final class IngredientRequirementSnapshot {
    private final String key;
    private final long count;
    private final boolean consumed;
    private final List<IngredientAlternativeSnapshot> alternatives;
    public IngredientRequirementSnapshot(String key, long count, boolean consumed, List<IngredientAlternativeSnapshot> alternatives) {

        key = ContextValidation.nonBlank(key, "key");
        if (count <= 0) {
            throw new IllegalArgumentException("ingredient count must be positive");
        }
        alternatives = dev.openallay.util.Java8Collections.listCopyOf(alternatives);
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("ingredient alternatives must not be empty");
        }
            this.key = key;
        this.count = count;
        this.consumed = consumed;
        this.alternatives = alternatives;
    }
    public String key() { return key; }
    public long count() { return count; }
    public boolean consumed() { return consumed; }
    public List<IngredientAlternativeSnapshot> alternatives() { return alternatives; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IngredientRequirementSnapshot)) return false;
        IngredientRequirementSnapshot that = (IngredientRequirementSnapshot) other;
        return java.util.Objects.equals(key, that.key) && count == that.count && consumed == that.consumed && java.util.Objects.equals(alternatives, that.alternatives);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + Long.hashCode(count);
        hash = 31 * hash + Boolean.hashCode(consumed);
        hash = 31 * hash + java.util.Objects.hashCode(alternatives);
        return hash;
    }
    @Override public String toString() { return "IngredientRequirementSnapshot[key=" + key + ", count=" + count + ", consumed=" + consumed + ", alternatives=" + alternatives + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<IngredientRequirementSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(IngredientRequirementSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<IngredientRequirementSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(IngredientRequirementSnapshot.class, "key", IngredientRequirementSnapshot::key),
                    new dev.openallay.value.ValueSchema.Component<>(IngredientRequirementSnapshot.class, "count", IngredientRequirementSnapshot::count),
                    new dev.openallay.value.ValueSchema.Component<>(IngredientRequirementSnapshot.class, "consumed", IngredientRequirementSnapshot::consumed),
                    new dev.openallay.value.ValueSchema.Component<>(IngredientRequirementSnapshot.class, "alternatives", IngredientRequirementSnapshot::alternatives)), arguments -> new IngredientRequirementSnapshot((String) arguments[0], (Long) arguments[1], (Boolean) arguments[2], (List) arguments[3]));
        }
    }
}
