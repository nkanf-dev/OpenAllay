package dev.openallay.guide;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Durable sum of actual provider calls. Estimated USD is the known portion, not an invoice. */
@dev.openallay.value.ValueType(GuideUsageSnapshot.ValueSchemaProvider.class)
public final class GuideUsageSnapshot {
    private final long inputTokens;
    private final long outputTokens;
    private final long cacheReadTokens;
    private final long cacheWriteTokens;
    private final int actualCalls;
    private final int reportedCalls;
    private final boolean incomplete;
    private final boolean cacheIncomplete;
    private final BigDecimal estimatedUsd;
    private final boolean costIncomplete;
    public GuideUsageSnapshot(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens, int actualCalls, int reportedCalls, boolean incomplete, boolean cacheIncomplete, BigDecimal estimatedUsd, boolean costIncomplete) {

        if (inputTokens < 0 || outputTokens < 0 || cacheReadTokens < 0 || cacheWriteTokens < 0
                || actualCalls < 0 || reportedCalls < 0 || reportedCalls > actualCalls
                || estimatedUsd != null && estimatedUsd.signum() < 0) {
            throw new IllegalArgumentException("Guide usage totals must be non-negative and consistent");
        }
        if (!cacheIncomplete && cacheReadTokens > inputTokens) {
            throw new IllegalArgumentException("Cache reads exceed total input");
        }

        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.cacheWriteTokens = cacheWriteTokens;
        this.actualCalls = actualCalls;
        this.reportedCalls = reportedCalls;
        this.incomplete = incomplete;
        this.cacheIncomplete = cacheIncomplete;
        this.estimatedUsd = estimatedUsd;
        this.costIncomplete = costIncomplete;
    }
    public long inputTokens() { return inputTokens; }
    public long outputTokens() { return outputTokens; }
    public long cacheReadTokens() { return cacheReadTokens; }
    public long cacheWriteTokens() { return cacheWriteTokens; }
    public int actualCalls() { return actualCalls; }
    public int reportedCalls() { return reportedCalls; }
    public boolean incomplete() { return incomplete; }
    public boolean cacheIncomplete() { return cacheIncomplete; }
    public BigDecimal estimatedUsd() { return estimatedUsd; }
    public boolean costIncomplete() { return costIncomplete; }
public static GuideUsageSnapshot empty() {
        return new GuideUsageSnapshot(0, 0, 0, 0, 0, 0, false, false, BigDecimal.ZERO, false);
    }
public static GuideUsageSnapshot unknown() {
        return new GuideUsageSnapshot(0, 0, 0, 0, 0, 0, true, true, null, true);
    }
public boolean known() { return reportedCalls > 0; }
public BigDecimal cacheHitRate() {
        if (cacheIncomplete || actualCalls == 0 || inputTokens == 0) return null;
        return BigDecimal.valueOf(cacheReadTokens).divide(BigDecimal.valueOf(inputTokens), 6, RoundingMode.HALF_UP);
    }
public GuideUsageSnapshot pending() { return pending(0); }
public GuideUsageSnapshot pending(int pendingCalls) {
        return new GuideUsageSnapshot(inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens,
                Math.addExact(actualCalls, pendingCalls), reportedCalls, true, true,
                actualCalls == 0 ? null : estimatedUsd, true);
    }
public GuideUsageSnapshot plus(GuideUsageSnapshot other) {
        if (actualCalls == 0 && !incomplete && !costIncomplete) return other;
        if (other.actualCalls == 0 && !other.incomplete && !other.costIncomplete) return this;
        BigDecimal amount = estimatedUsd == null ? other.estimatedUsd
                : other.estimatedUsd == null ? estimatedUsd : estimatedUsd.add(other.estimatedUsd);
        return new GuideUsageSnapshot(
                Math.addExact(inputTokens, other.inputTokens), Math.addExact(outputTokens, other.outputTokens),
                Math.addExact(cacheReadTokens, other.cacheReadTokens), Math.addExact(cacheWriteTokens, other.cacheWriteTokens),
                Math.addExact(actualCalls, other.actualCalls), Math.addExact(reportedCalls, other.reportedCalls),
                incomplete || other.incomplete, cacheIncomplete || other.cacheIncomplete,
                amount, costIncomplete || other.costIncomplete);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideUsageSnapshot)) return false;
        GuideUsageSnapshot that = (GuideUsageSnapshot) other;
        return inputTokens == that.inputTokens && outputTokens == that.outputTokens && cacheReadTokens == that.cacheReadTokens && cacheWriteTokens == that.cacheWriteTokens && actualCalls == that.actualCalls && reportedCalls == that.reportedCalls && incomplete == that.incomplete && cacheIncomplete == that.cacheIncomplete && java.util.Objects.equals(estimatedUsd, that.estimatedUsd) && costIncomplete == that.costIncomplete;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(inputTokens);
        hash = 31 * hash + Long.hashCode(outputTokens);
        hash = 31 * hash + Long.hashCode(cacheReadTokens);
        hash = 31 * hash + Long.hashCode(cacheWriteTokens);
        hash = 31 * hash + Integer.hashCode(actualCalls);
        hash = 31 * hash + Integer.hashCode(reportedCalls);
        hash = 31 * hash + Boolean.hashCode(incomplete);
        hash = 31 * hash + Boolean.hashCode(cacheIncomplete);
        hash = 31 * hash + java.util.Objects.hashCode(estimatedUsd);
        hash = 31 * hash + Boolean.hashCode(costIncomplete);
        return hash;
    }
    @Override public String toString() { return "GuideUsageSnapshot[inputTokens=" + inputTokens + ", outputTokens=" + outputTokens + ", cacheReadTokens=" + cacheReadTokens + ", cacheWriteTokens=" + cacheWriteTokens + ", actualCalls=" + actualCalls + ", reportedCalls=" + reportedCalls + ", incomplete=" + incomplete + ", cacheIncomplete=" + cacheIncomplete + ", estimatedUsd=" + estimatedUsd + ", costIncomplete=" + costIncomplete + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideUsageSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideUsageSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideUsageSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideUsageSnapshot.class, "inputTokens", GuideUsageSnapshot::inputTokens), new dev.openallay.value.ValueSchema.Component<>(GuideUsageSnapshot.class, "outputTokens", GuideUsageSnapshot::outputTokens), new dev.openallay.value.ValueSchema.Component<>(GuideUsageSnapshot.class, "cacheReadTokens", GuideUsageSnapshot::cacheReadTokens), new dev.openallay.value.ValueSchema.Component<>(GuideUsageSnapshot.class, "cacheWriteTokens", GuideUsageSnapshot::cacheWriteTokens), new dev.openallay.value.ValueSchema.Component<>(GuideUsageSnapshot.class, "actualCalls", GuideUsageSnapshot::actualCalls), new dev.openallay.value.ValueSchema.Component<>(GuideUsageSnapshot.class, "reportedCalls", GuideUsageSnapshot::reportedCalls), new dev.openallay.value.ValueSchema.Component<>(GuideUsageSnapshot.class, "incomplete", GuideUsageSnapshot::incomplete), new dev.openallay.value.ValueSchema.Component<>(GuideUsageSnapshot.class, "cacheIncomplete", GuideUsageSnapshot::cacheIncomplete), new dev.openallay.value.ValueSchema.Component<>(GuideUsageSnapshot.class, "estimatedUsd", GuideUsageSnapshot::estimatedUsd), new dev.openallay.value.ValueSchema.Component<>(GuideUsageSnapshot.class, "costIncomplete", GuideUsageSnapshot::costIncomplete)), arguments -> new GuideUsageSnapshot((Long) arguments[0], (Long) arguments[1], (Long) arguments[2], (Long) arguments[3], (Integer) arguments[4], (Integer) arguments[5], (Boolean) arguments[6], (Boolean) arguments[7], (BigDecimal) arguments[8], (Boolean) arguments[9]));
        }
    }
}
