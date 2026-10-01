package dev.openallay.guide;

import java.math.BigDecimal;

/** Runtime-only provider-reported counts. Missing reports are not zero-token calls. */
public record GuideUsageSnapshot(
        long inputTokens, long outputTokens, int reportedCalls,
        boolean incomplete, BigDecimal estimatedUsd) {
    public static GuideUsageSnapshot unknown() {
        return new GuideUsageSnapshot(0, 0, 0, true, null);
    }

    public boolean known() { return reportedCalls > 0; }
}
