package dev.openallay.context;

@dev.openallay.value.ValueType(RecipeProcessingSnapshot.ValueSchemaProvider.class)
public final class RecipeProcessingSnapshot {
    private final Long durationTicks;
    private final Long energy;
    private final Double temperature;
    public RecipeProcessingSnapshot(Long durationTicks, Long energy, Double temperature) {

        if (durationTicks != null && durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks must not be negative");
        }
        if (energy != null && energy < 0) {
            throw new IllegalArgumentException("energy must not be negative");
        }
        if (temperature != null && !Double.isFinite(temperature)) {
            throw new IllegalArgumentException("temperature must be finite");
        }
            this.durationTicks = durationTicks;
        this.energy = energy;
        this.temperature = temperature;
    }
    public Long durationTicks() { return durationTicks; }
    public Long energy() { return energy; }
    public Double temperature() { return temperature; }



    public static RecipeProcessingSnapshot unknown() {
        return new RecipeProcessingSnapshot(null, null, null);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeProcessingSnapshot)) return false;
        RecipeProcessingSnapshot that = (RecipeProcessingSnapshot) other;
        return java.util.Objects.equals(durationTicks, that.durationTicks) && java.util.Objects.equals(energy, that.energy) && java.util.Objects.equals(temperature, that.temperature);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(durationTicks);
        hash = 31 * hash + java.util.Objects.hashCode(energy);
        hash = 31 * hash + java.util.Objects.hashCode(temperature);
        return hash;
    }
    @Override public String toString() { return "RecipeProcessingSnapshot[durationTicks=" + durationTicks + ", energy=" + energy + ", temperature=" + temperature + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeProcessingSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeProcessingSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeProcessingSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProcessingSnapshot.class, "durationTicks", RecipeProcessingSnapshot::durationTicks),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProcessingSnapshot.class, "energy", RecipeProcessingSnapshot::energy),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProcessingSnapshot.class, "temperature", RecipeProcessingSnapshot::temperature)), arguments -> new RecipeProcessingSnapshot((Long) arguments[0], (Long) arguments[1], (Double) arguments[2]));
        }
    }
}
