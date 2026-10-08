package dev.openallay.context;

@dev.openallay.value.ValueType(ContextMetrics.ValueSchemaProvider.class)
public final class ContextMetrics {
    private final long registryEntries;
    private final long recipes;
    private final long inventorySlots;
    private final long estimatedSerializedBytes;
    private final long captureNanos;
    public ContextMetrics(long registryEntries, long recipes, long inventorySlots, long estimatedSerializedBytes, long captureNanos) {

        ContextValidation.nonNegative(registryEntries, "registryEntries");
        ContextValidation.nonNegative(recipes, "recipes");
        ContextValidation.nonNegative(inventorySlots, "inventorySlots");
        ContextValidation.nonNegative(estimatedSerializedBytes, "estimatedSerializedBytes");
        ContextValidation.nonNegative(captureNanos, "captureNanos");
            this.registryEntries = registryEntries;
        this.recipes = recipes;
        this.inventorySlots = inventorySlots;
        this.estimatedSerializedBytes = estimatedSerializedBytes;
        this.captureNanos = captureNanos;
    }
    public long registryEntries() { return registryEntries; }
    public long recipes() { return recipes; }
    public long inventorySlots() { return inventorySlots; }
    public long estimatedSerializedBytes() { return estimatedSerializedBytes; }
    public long captureNanos() { return captureNanos; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ContextMetrics)) return false;
        ContextMetrics that = (ContextMetrics) other;
        return registryEntries == that.registryEntries && recipes == that.recipes && inventorySlots == that.inventorySlots && estimatedSerializedBytes == that.estimatedSerializedBytes && captureNanos == that.captureNanos;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(registryEntries);
        hash = 31 * hash + Long.hashCode(recipes);
        hash = 31 * hash + Long.hashCode(inventorySlots);
        hash = 31 * hash + Long.hashCode(estimatedSerializedBytes);
        hash = 31 * hash + Long.hashCode(captureNanos);
        return hash;
    }
    @Override public String toString() { return "ContextMetrics[registryEntries=" + registryEntries + ", recipes=" + recipes + ", inventorySlots=" + inventorySlots + ", estimatedSerializedBytes=" + estimatedSerializedBytes + ", captureNanos=" + captureNanos + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ContextMetrics> schema() {
            return new dev.openallay.value.ValueSchema<>(ContextMetrics.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ContextMetrics>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(ContextMetrics.class, "registryEntries", ContextMetrics::registryEntries),
                    new dev.openallay.value.ValueSchema.Component<>(ContextMetrics.class, "recipes", ContextMetrics::recipes),
                    new dev.openallay.value.ValueSchema.Component<>(ContextMetrics.class, "inventorySlots", ContextMetrics::inventorySlots),
                    new dev.openallay.value.ValueSchema.Component<>(ContextMetrics.class, "estimatedSerializedBytes", ContextMetrics::estimatedSerializedBytes),
                    new dev.openallay.value.ValueSchema.Component<>(ContextMetrics.class, "captureNanos", ContextMetrics::captureNanos)), arguments -> new ContextMetrics((Long) arguments[0], (Long) arguments[1], (Long) arguments[2], (Long) arguments[3], (Long) arguments[4]));
        }
    }
}
