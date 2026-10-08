package dev.openallay.guide;

import dev.openallay.agent.context.ContextCheckpoint;

/** Actual manual-control outcome. It is not a model answer or an Agent request. */
@dev.openallay.value.ValueType(GuideCompactResult.ValueSchemaProvider.class)
public final class GuideCompactResult {
    private final Status status;
    private final int beforeTokens;
    private final int afterTokens;
    private final int inputBudget;
    private final ContextCheckpoint checkpoint;
    public GuideCompactResult(Status status, int beforeTokens, int afterTokens, int inputBudget, ContextCheckpoint checkpoint) {

        java.util.Objects.requireNonNull(status, "status");
        if (beforeTokens < 0 || afterTokens < 0 || inputBudget < 1) {
            throw new IllegalArgumentException("Invalid compaction budget");
        }
        if ((status == Status.COMPACTED) != (checkpoint != null)) {
            throw new IllegalArgumentException("Compaction outcome must match its actual checkpoint");
        }

        this.status = status;
        this.beforeTokens = beforeTokens;
        this.afterTokens = afterTokens;
        this.inputBudget = inputBudget;
        this.checkpoint = checkpoint;
    }
    public Status status() { return status; }
    public int beforeTokens() { return beforeTokens; }
    public int afterTokens() { return afterTokens; }
    public int inputBudget() { return inputBudget; }
    public ContextCheckpoint checkpoint() { return checkpoint; }
public enum Status { COMPACTED, NOT_NEEDED }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideCompactResult)) return false;
        GuideCompactResult that = (GuideCompactResult) other;
        return java.util.Objects.equals(status, that.status) && beforeTokens == that.beforeTokens && afterTokens == that.afterTokens && inputBudget == that.inputBudget && java.util.Objects.equals(checkpoint, that.checkpoint);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + Integer.hashCode(beforeTokens);
        hash = 31 * hash + Integer.hashCode(afterTokens);
        hash = 31 * hash + Integer.hashCode(inputBudget);
        hash = 31 * hash + java.util.Objects.hashCode(checkpoint);
        return hash;
    }
    @Override public String toString() { return "GuideCompactResult[status=" + status + ", beforeTokens=" + beforeTokens + ", afterTokens=" + afterTokens + ", inputBudget=" + inputBudget + ", checkpoint=" + checkpoint + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideCompactResult> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideCompactResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideCompactResult>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideCompactResult.class, "status", GuideCompactResult::status), new dev.openallay.value.ValueSchema.Component<>(GuideCompactResult.class, "beforeTokens", GuideCompactResult::beforeTokens), new dev.openallay.value.ValueSchema.Component<>(GuideCompactResult.class, "afterTokens", GuideCompactResult::afterTokens), new dev.openallay.value.ValueSchema.Component<>(GuideCompactResult.class, "inputBudget", GuideCompactResult::inputBudget), new dev.openallay.value.ValueSchema.Component<>(GuideCompactResult.class, "checkpoint", GuideCompactResult::checkpoint)), arguments -> new GuideCompactResult((Status) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (ContextCheckpoint) arguments[4]));
        }
    }
}
