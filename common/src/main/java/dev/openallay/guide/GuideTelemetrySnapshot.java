package dev.openallay.guide;

import java.util.UUID;

/** Small immutable footer projection. Contains no prompt, transcript, credentials or reasoning. */
public record GuideTelemetrySnapshot(
        String sessionId, GuideModelSelection selection, UUID requestId,
        GuideContextEstimate context, GuideUsageSnapshot requestUsage,
        GuideUsageSnapshot sessionUsage) {
    public static GuideTelemetrySnapshot unknown(String sessionId, GuideModelSelection selection) {
        return new GuideTelemetrySnapshot(sessionId, selection, null, null,
                GuideUsageSnapshot.unknown(), GuideUsageSnapshot.unknown());
    }
}
