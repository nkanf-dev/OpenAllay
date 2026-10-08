package dev.openallay.trace.replay;

@dev.openallay.value.ValueType(ReplayMetrics.ValueSchemaProvider.class)
public final class ReplayMetrics {
    private final long registryEntries;
    private final long recipes;
    private final long inventorySlots;
    private final long contextEstimatedSerializedBytes;
    private final long contextCaptureNanos;
    private final long toolResultSerializedBytes;
    private final long totalDurationNanos;
    public ReplayMetrics(long registryEntries, long recipes, long inventorySlots, long contextEstimatedSerializedBytes, long contextCaptureNanos, long toolResultSerializedBytes, long totalDurationNanos) {

        if (registryEntries < 0
                || recipes < 0
                || inventorySlots < 0
                || contextEstimatedSerializedBytes < 0
                || contextCaptureNanos < 0
                || toolResultSerializedBytes < 0
                || totalDurationNanos < 0) {
            throw new IllegalArgumentException("Replay metrics must be non-negative");
        }

        this.registryEntries = registryEntries;
        this.recipes = recipes;
        this.inventorySlots = inventorySlots;
        this.contextEstimatedSerializedBytes = contextEstimatedSerializedBytes;
        this.contextCaptureNanos = contextCaptureNanos;
        this.toolResultSerializedBytes = toolResultSerializedBytes;
        this.totalDurationNanos = totalDurationNanos;
    }
    public long registryEntries() { return registryEntries; }
    public long recipes() { return recipes; }
    public long inventorySlots() { return inventorySlots; }
    public long contextEstimatedSerializedBytes() { return contextEstimatedSerializedBytes; }
    public long contextCaptureNanos() { return contextCaptureNanos; }
    public long toolResultSerializedBytes() { return toolResultSerializedBytes; }
    public long totalDurationNanos() { return totalDurationNanos; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReplayMetrics)) return false;
        ReplayMetrics that = (ReplayMetrics) other;
        return registryEntries == that.registryEntries && recipes == that.recipes && inventorySlots == that.inventorySlots && contextEstimatedSerializedBytes == that.contextEstimatedSerializedBytes && contextCaptureNanos == that.contextCaptureNanos && toolResultSerializedBytes == that.toolResultSerializedBytes && totalDurationNanos == that.totalDurationNanos;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(registryEntries);
        hash = 31 * hash + Long.hashCode(recipes);
        hash = 31 * hash + Long.hashCode(inventorySlots);
        hash = 31 * hash + Long.hashCode(contextEstimatedSerializedBytes);
        hash = 31 * hash + Long.hashCode(contextCaptureNanos);
        hash = 31 * hash + Long.hashCode(toolResultSerializedBytes);
        hash = 31 * hash + Long.hashCode(totalDurationNanos);
        return hash;
    }
    @Override public String toString() { return "ReplayMetrics[registryEntries=" + registryEntries + ", recipes=" + recipes + ", inventorySlots=" + inventorySlots + ", contextEstimatedSerializedBytes=" + contextEstimatedSerializedBytes + ", contextCaptureNanos=" + contextCaptureNanos + ", toolResultSerializedBytes=" + toolResultSerializedBytes + ", totalDurationNanos=" + totalDurationNanos + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ReplayMetrics> schema() {
            return new dev.openallay.value.ValueSchema<>(ReplayMetrics.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ReplayMetrics>>asList(new dev.openallay.value.ValueSchema.Component<>(ReplayMetrics.class, "registryEntries", ReplayMetrics::registryEntries), new dev.openallay.value.ValueSchema.Component<>(ReplayMetrics.class, "recipes", ReplayMetrics::recipes), new dev.openallay.value.ValueSchema.Component<>(ReplayMetrics.class, "inventorySlots", ReplayMetrics::inventorySlots), new dev.openallay.value.ValueSchema.Component<>(ReplayMetrics.class, "contextEstimatedSerializedBytes", ReplayMetrics::contextEstimatedSerializedBytes), new dev.openallay.value.ValueSchema.Component<>(ReplayMetrics.class, "contextCaptureNanos", ReplayMetrics::contextCaptureNanos), new dev.openallay.value.ValueSchema.Component<>(ReplayMetrics.class, "toolResultSerializedBytes", ReplayMetrics::toolResultSerializedBytes), new dev.openallay.value.ValueSchema.Component<>(ReplayMetrics.class, "totalDurationNanos", ReplayMetrics::totalDurationNanos)), arguments -> new ReplayMetrics((Long) arguments[0], (Long) arguments[1], (Long) arguments[2], (Long) arguments[3], (Long) arguments[4], (Long) arguments[5], (Long) arguments[6]));
        }
    }
}
