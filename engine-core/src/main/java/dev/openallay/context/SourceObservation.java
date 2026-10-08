package dev.openallay.context;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A compact origin and actual capture-time extent; not a count of captures or reads. */
@dev.openallay.value.ValueType(SourceObservation.ValueSchemaProvider.class)
public final class SourceObservation {
    private final EvidenceMetadata evidence;
    private final Instant lastCapturedAt;
    public SourceObservation(EvidenceMetadata evidence, Instant lastCapturedAt) {

        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(lastCapturedAt, "lastCapturedAt");
        if (lastCapturedAt.isBefore(evidence.capturedAt())) {
            throw new IllegalArgumentException("lastCapturedAt must not precede the first capture");
        }
            this.evidence = evidence;
        this.lastCapturedAt = lastCapturedAt;
    }
    public EvidenceMetadata evidence() { return evidence; }
    public Instant lastCapturedAt() { return lastCapturedAt; }

    private static final String CAPTURE_START = "openallay_builder:capture_start";
    private static final String CAPTURE_END = "openallay_builder:capture_end";



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
        return dev.openallay.util.Java8Collections.mapCopyOf(identity);
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

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SourceObservation)) return false;
        SourceObservation that = (SourceObservation) other;
        return java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(lastCapturedAt, that.lastCapturedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(lastCapturedAt);
        return hash;
    }
    @Override public String toString() { return "SourceObservation[evidence=" + evidence + ", lastCapturedAt=" + lastCapturedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SourceObservation> schema() {
            return new dev.openallay.value.ValueSchema<>(SourceObservation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SourceObservation>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(SourceObservation.class, "evidence", SourceObservation::evidence),
                    new dev.openallay.value.ValueSchema.Component<>(SourceObservation.class, "lastCapturedAt", SourceObservation::lastCapturedAt)), arguments -> new SourceObservation((EvidenceMetadata) arguments[0], (Instant) arguments[1]));
        }
    }
}
