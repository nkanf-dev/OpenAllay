package dev.openallay.model;

/** Canonical provider counts. Input includes cache reads and writes; absent counts stay unknown. */
@dev.openallay.value.ValueType(ModelUsage.ValueSchemaProvider.class)
public final class ModelUsage {
    private final long inputTokens;
    private final long outputTokens;
    private final long cacheReadTokens;
    private final long cacheWriteTokens;
    private final long uncachedInputTokens;
    private final boolean inputKnown;
    private final boolean outputKnown;
    private final boolean cacheReadKnown;
    private final boolean cacheWriteKnown;
    private final boolean uncachedInputKnown;
    public ModelUsage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens, long uncachedInputTokens, boolean inputKnown, boolean outputKnown, boolean cacheReadKnown, boolean cacheWriteKnown, boolean uncachedInputKnown) {

        if (inputTokens < 0 || outputTokens < 0 || cacheReadTokens < 0
                || cacheWriteTokens < 0 || uncachedInputTokens < 0) {
            throw new IllegalArgumentException("Model usage values must be non-negative");
        }
        if (!outputKnown && outputTokens != 0 || !cacheReadKnown && cacheReadTokens != 0
                || !cacheWriteKnown && cacheWriteTokens != 0 || !uncachedInputKnown && uncachedInputTokens != 0) {
            throw new IllegalArgumentException("Unknown usage categories cannot carry reported counts");
        }
        long categorized = Math.addExact(Math.addExact(uncachedInputTokens, cacheReadTokens), cacheWriteTokens);
        if (inputKnown && categorized > inputTokens) {
            throw new IllegalArgumentException("Cache counts exceed total model input");
        }
        if (inputKnown && uncachedInputKnown && cacheReadKnown && cacheWriteKnown
                && categorized != inputTokens) {
            throw new IllegalArgumentException("Model input categories must sum to total input");
        }

        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.cacheWriteTokens = cacheWriteTokens;
        this.uncachedInputTokens = uncachedInputTokens;
        this.inputKnown = inputKnown;
        this.outputKnown = outputKnown;
        this.cacheReadKnown = cacheReadKnown;
        this.cacheWriteKnown = cacheWriteKnown;
        this.uncachedInputKnown = uncachedInputKnown;
    }
    public long inputTokens() { return inputTokens; }
    public long outputTokens() { return outputTokens; }
    public long cacheReadTokens() { return cacheReadTokens; }
    public long cacheWriteTokens() { return cacheWriteTokens; }
    public long uncachedInputTokens() { return uncachedInputTokens; }
    public boolean inputKnown() { return inputKnown; }
    public boolean outputKnown() { return outputKnown; }
    public boolean cacheReadKnown() { return cacheReadKnown; }
    public boolean cacheWriteKnown() { return cacheWriteKnown; }
    public boolean uncachedInputKnown() { return uncachedInputKnown; }
public ModelUsage(long inputTokens, long outputTokens, long cacheReadTokens) {
        this(inputTokens, outputTokens, cacheReadTokens, 0,
                Math.subtractExact(inputTokens, cacheReadTokens), true, true, true, true, true);
    }
public static ModelUsage openAi(
            long input, boolean inputKnown, long output, boolean outputKnown,
            long cacheRead, boolean cacheReadKnown) {
        return new ModelUsage(input, output, cacheRead, 0,
                inputKnown && cacheReadKnown ? Math.subtractExact(input, cacheRead) : 0,
                inputKnown, outputKnown, cacheReadKnown, true, inputKnown && cacheReadKnown);
    }
public static ModelUsage anthropic(
            long uncached, boolean uncachedKnown, long output, boolean outputKnown,
            long cacheRead, boolean cacheReadKnown, long cacheWrite, boolean cacheWriteKnown) {
        return new ModelUsage(Math.addExact(Math.addExact(uncached, cacheRead), cacheWrite),
                output, cacheRead, cacheWrite, uncached,
                uncachedKnown && cacheReadKnown && cacheWriteKnown, outputKnown,
                cacheReadKnown, cacheWriteKnown, uncachedKnown);
    }
public static ModelUsage empty() {
        return new ModelUsage(0, 0, 0, 0, 0, false, false, false, false, false);
    }
public boolean reported() {
        return inputKnown || outputKnown || uncachedInputKnown || cacheReadKnown || cacheWriteTokens > 0;
    }
public boolean complete() {
        return inputKnown && outputKnown && cacheReadKnown && cacheWriteKnown && uncachedInputKnown;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelUsage)) return false;
        ModelUsage that = (ModelUsage) other;
        return inputTokens == that.inputTokens && outputTokens == that.outputTokens && cacheReadTokens == that.cacheReadTokens && cacheWriteTokens == that.cacheWriteTokens && uncachedInputTokens == that.uncachedInputTokens && inputKnown == that.inputKnown && outputKnown == that.outputKnown && cacheReadKnown == that.cacheReadKnown && cacheWriteKnown == that.cacheWriteKnown && uncachedInputKnown == that.uncachedInputKnown;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(inputTokens);
        hash = 31 * hash + Long.hashCode(outputTokens);
        hash = 31 * hash + Long.hashCode(cacheReadTokens);
        hash = 31 * hash + Long.hashCode(cacheWriteTokens);
        hash = 31 * hash + Long.hashCode(uncachedInputTokens);
        hash = 31 * hash + Boolean.hashCode(inputKnown);
        hash = 31 * hash + Boolean.hashCode(outputKnown);
        hash = 31 * hash + Boolean.hashCode(cacheReadKnown);
        hash = 31 * hash + Boolean.hashCode(cacheWriteKnown);
        hash = 31 * hash + Boolean.hashCode(uncachedInputKnown);
        return hash;
    }
    @Override public String toString() { return "ModelUsage[inputTokens=" + inputTokens + ", outputTokens=" + outputTokens + ", cacheReadTokens=" + cacheReadTokens + ", cacheWriteTokens=" + cacheWriteTokens + ", uncachedInputTokens=" + uncachedInputTokens + ", inputKnown=" + inputKnown + ", outputKnown=" + outputKnown + ", cacheReadKnown=" + cacheReadKnown + ", cacheWriteKnown=" + cacheWriteKnown + ", uncachedInputKnown=" + uncachedInputKnown + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelUsage> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelUsage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelUsage>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelUsage.class, "inputTokens", ModelUsage::inputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelUsage.class, "outputTokens", ModelUsage::outputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelUsage.class, "cacheReadTokens", ModelUsage::cacheReadTokens), new dev.openallay.value.ValueSchema.Component<>(ModelUsage.class, "cacheWriteTokens", ModelUsage::cacheWriteTokens), new dev.openallay.value.ValueSchema.Component<>(ModelUsage.class, "uncachedInputTokens", ModelUsage::uncachedInputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelUsage.class, "inputKnown", ModelUsage::inputKnown), new dev.openallay.value.ValueSchema.Component<>(ModelUsage.class, "outputKnown", ModelUsage::outputKnown), new dev.openallay.value.ValueSchema.Component<>(ModelUsage.class, "cacheReadKnown", ModelUsage::cacheReadKnown), new dev.openallay.value.ValueSchema.Component<>(ModelUsage.class, "cacheWriteKnown", ModelUsage::cacheWriteKnown), new dev.openallay.value.ValueSchema.Component<>(ModelUsage.class, "uncachedInputKnown", ModelUsage::uncachedInputKnown)), arguments -> new ModelUsage((Long) arguments[0], (Long) arguments[1], (Long) arguments[2], (Long) arguments[3], (Long) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (Boolean) arguments[7], (Boolean) arguments[8], (Boolean) arguments[9]));
        }
    }
}
