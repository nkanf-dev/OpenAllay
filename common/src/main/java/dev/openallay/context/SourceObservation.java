package dev.openallay.context;

import java.time.Instant;
import java.util.Objects;

/** A compact origin and actual capture-time extent; not a count of captures or reads. */
public record SourceObservation(
        EvidenceMetadata evidence, Instant lastCapturedAt) {
    public SourceObservation {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(lastCapturedAt, "lastCapturedAt");
        if (lastCapturedAt.isBefore(evidence.capturedAt())) {
            throw new IllegalArgumentException("lastCapturedAt must not precede the first capture");
        }
    }

    public SourceObservation(EvidenceMetadata evidence) {
        this(evidence, evidence.capturedAt());
    }
}
