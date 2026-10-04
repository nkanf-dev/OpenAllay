package dev.openallay.context;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A compact origin and actual capture-time extent; not a count of captures or reads. */
public record SourceObservation(
        EvidenceMetadata evidence, Instant lastCapturedAt) {
    private static final String CAPTURE_START = "openallay_builder:capture_start";
    private static final String CAPTURE_END = "openallay_builder:capture_end";

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

    /** The real start of the retained capture, without replacing its original metadata. */
    public Instant firstCapturedAt() {
        Instant start = builderCaptureStart(evidence);
        return start == null ? evidence.capturedAt() : start;
    }

    /** Details that identify the origin; only a valid Builder capture pair is extent. */
    public Map<String, String> identityDetails() {
        return identityDetails(evidence);
    }

    static Map<String, String> identityDetails(EvidenceMetadata evidence) {
        if (builderCaptureStart(evidence) == null) {
            return evidence.details();
        }
        Map<String, String> identity = new LinkedHashMap<>(evidence.details());
        identity.remove(CAPTURE_START);
        identity.remove(CAPTURE_END);
        return Map.copyOf(identity);
    }

    private static Instant builderCaptureStart(EvidenceMetadata evidence) {
        String rawStart = evidence.details().get(CAPTURE_START);
        String rawEnd = evidence.details().get(CAPTURE_END);
        if (rawStart == null || rawEnd == null) return null;
        try {
            Instant start = Instant.parse(rawStart);
            Instant end = Instant.parse(rawEnd);
            // Only this complete, consistent capture pair is extent rather than source identity.
            // Unknown, malformed, or partial details must remain in identity unchanged.
            return !start.isAfter(end) && end.equals(evidence.capturedAt()) ? start : null;
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }
}
