package dev.openallay.guide;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.SourceObservation;
import java.time.Instant;

public record GuideSource(
        String toolId, EvidenceMetadata evidence, Instant lastCapturedAt) {
    public GuideSource {
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("toolId must not be blank");
        }
        new SourceObservation(evidence, lastCapturedAt);
    }

    public GuideSource(String toolId, EvidenceMetadata evidence) {
        this(toolId, evidence, evidence.capturedAt());
    }
}
