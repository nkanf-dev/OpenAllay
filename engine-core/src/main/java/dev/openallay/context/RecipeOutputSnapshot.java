package dev.openallay.context;

import java.util.Objects;

@dev.openallay.value.ValueType(RecipeOutputSnapshot.ValueSchemaProvider.class)
public final class RecipeOutputSnapshot {
    private final ItemStackSnapshot stack;
    private final double probability;
    public RecipeOutputSnapshot(ItemStackSnapshot stack, double probability) {

        Objects.requireNonNull(stack, "stack");
        if (!Double.isFinite(probability) || probability < 0.0D || probability > 1.0D) {
            throw new IllegalArgumentException("output probability must be between zero and one");
        }
            this.stack = stack;
        this.probability = probability;
    }
    public ItemStackSnapshot stack() { return stack; }
    public double probability() { return probability; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeOutputSnapshot)) return false;
        RecipeOutputSnapshot that = (RecipeOutputSnapshot) other;
        return java.util.Objects.equals(stack, that.stack) && Double.compare(probability, that.probability) == 0;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(stack);
        hash = 31 * hash + Double.hashCode(probability);
        return hash;
    }
    @Override public String toString() { return "RecipeOutputSnapshot[stack=" + stack + ", probability=" + probability + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeOutputSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeOutputSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeOutputSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(RecipeOutputSnapshot.class, "stack", RecipeOutputSnapshot::stack),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeOutputSnapshot.class, "probability", RecipeOutputSnapshot::probability)), arguments -> new RecipeOutputSnapshot((ItemStackSnapshot) arguments[0], (Double) arguments[1]));
        }
    }
}
