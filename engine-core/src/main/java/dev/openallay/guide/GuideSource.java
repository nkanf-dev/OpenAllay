package dev.openallay.guide;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.SourceObservation;
import java.time.Instant;

@dev.openallay.value.ValueType(GuideSource.ValueSchemaProvider.class)
public final class GuideSource {
    private final String toolId;
    private final EvidenceMetadata evidence;
    private final Instant lastCapturedAt;
    public GuideSource(String toolId, EvidenceMetadata evidence, Instant lastCapturedAt) {

        if (toolId == null || dev.openallay.util.Java8Strings.isBlank(toolId)) {
            throw new IllegalArgumentException("toolId must not be blank");
        }
        new SourceObservation(evidence, lastCapturedAt);

        this.toolId = toolId;
        this.evidence = evidence;
        this.lastCapturedAt = lastCapturedAt;
    }
    public String toolId() { return toolId; }
    public EvidenceMetadata evidence() { return evidence; }
    public Instant lastCapturedAt() { return lastCapturedAt; }
public GuideSource(String toolId, EvidenceMetadata evidence) {
        this(toolId, evidence, evidence.capturedAt());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideSource)) return false;
        GuideSource that = (GuideSource) other;
        return java.util.Objects.equals(toolId, that.toolId) && java.util.Objects.equals(evidence, that.evidence) && java.util.Objects.equals(lastCapturedAt, that.lastCapturedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        hash = 31 * hash + java.util.Objects.hashCode(lastCapturedAt);
        return hash;
    }
    @Override public String toString() { return "GuideSource[toolId=" + toolId + ", evidence=" + evidence + ", lastCapturedAt=" + lastCapturedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideSource> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideSource.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideSource>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideSource.class, "toolId", GuideSource::toolId), new dev.openallay.value.ValueSchema.Component<>(GuideSource.class, "evidence", GuideSource::evidence), new dev.openallay.value.ValueSchema.Component<>(GuideSource.class, "lastCapturedAt", GuideSource::lastCapturedAt)), arguments -> new GuideSource((String) arguments[0], (EvidenceMetadata) arguments[1], (Instant) arguments[2]));
        }
    }
}
