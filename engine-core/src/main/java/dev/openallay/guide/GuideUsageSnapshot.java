package dev.openallay.guide;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Durable sum of actual provider calls. Estimated USD is the known portion, not an invoice. */
public record GuideUsageSnapshot(
        long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens,
        int actualCalls, int reportedCalls, boolean incomplete, boolean cacheIncomplete,
        BigDecimal estimatedUsd, boolean costIncomplete) {
    public GuideUsageSnapshot {
        if (inputTokens < 0 || outputTokens < 0 || cacheReadTokens < 0 || cacheWriteTokens < 0
                || actualCalls < 0 || reportedCalls < 0 || reportedCalls > actualCalls
                || estimatedUsd != null && estimatedUsd.signum() < 0) {
            throw new IllegalArgumentException("Guide usage totals must be non-negative and consistent");
        }
        if (!cacheIncomplete && cacheReadTokens > inputTokens) {
            throw new IllegalArgumentException("Cache reads exceed total input");
        }
    }

    public static GuideUsageSnapshot empty() {
        return new GuideUsageSnapshot(0, 0, 0, 0, 0, 0, false, false, BigDecimal.ZERO, false);
    }

    public static GuideUsageSnapshot unknown() {
        return new GuideUsageSnapshot(0, 0, 0, 0, 0, 0, true, true, null, true);
    }

    public boolean known() { return reportedCalls > 0; }

    /** Cache creation is not a hit. A missing cache count is not an explicit zero. */
    public BigDecimal cacheHitRate() {
        if (cacheIncomplete || actualCalls == 0 || inputTokens == 0) return null;
        return BigDecimal.valueOf(cacheReadTokens).divide(BigDecimal.valueOf(inputTokens), 6, RoundingMode.HALF_UP);
    }

    public GuideUsageSnapshot pending() { return pending(0); }

    /** Provisional actual dispatches count once; their missing final reports remain explicit. */
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
}
