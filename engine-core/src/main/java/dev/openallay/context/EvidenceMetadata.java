package dev.openallay.context;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@dev.openallay.value.ValueType(EvidenceMetadata.ValueSchemaProvider.class)
public final class EvidenceMetadata {
    private final DataAuthority authority;
    private final DataCompleteness completeness;
    private final Instant capturedAt;
    private final String sourceId;
    private final String provenance;
    private final String gameVersion;
    private final String loader;
    private final Map<String, String> details;
    public EvidenceMetadata(DataAuthority authority, DataCompleteness completeness, Instant capturedAt, String sourceId, String provenance, String gameVersion, String loader, Map<String, String> details) {

        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(completeness, "completeness");
        Objects.requireNonNull(capturedAt, "capturedAt");
        sourceId = ContextValidation.identifier(sourceId, "sourceId");
        provenance = ContextValidation.identifier(provenance, "provenance");
        gameVersion = ContextValidation.nonBlank(gameVersion, "gameVersion");
        loader = ContextValidation.nonBlank(loader, "loader");
        TreeMap<String, String> copy = new TreeMap<>();
        Objects.requireNonNull(details, "details").forEach((key, value) -> copy.put(
                ContextValidation.identifier(key, "detail key"),
                ContextValidation.nonBlank(value, "detail value")));
        details = Collections.unmodifiableMap(copy);
        this.authority = authority;
        this.completeness = completeness;
        this.capturedAt = capturedAt;
        this.sourceId = sourceId;
        this.provenance = provenance;
        this.gameVersion = gameVersion;
        this.loader = loader;
        this.details = details;
    }
    public DataAuthority authority() { return authority; }
    public DataCompleteness completeness() { return completeness; }
    public Instant capturedAt() { return capturedAt; }
    public String sourceId() { return sourceId; }
    public String provenance() { return provenance; }
    public String gameVersion() { return gameVersion; }
    public String loader() { return loader; }
    public Map<String, String> details() { return details; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EvidenceMetadata)) return false;
        EvidenceMetadata that = (EvidenceMetadata) other;
        return java.util.Objects.equals(authority, that.authority) && java.util.Objects.equals(completeness, that.completeness) && java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(provenance, that.provenance) && java.util.Objects.equals(gameVersion, that.gameVersion) && java.util.Objects.equals(loader, that.loader) && java.util.Objects.equals(details, that.details);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(authority);
        hash = 31 * hash + java.util.Objects.hashCode(completeness);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        hash = 31 * hash + java.util.Objects.hashCode(gameVersion);
        hash = 31 * hash + java.util.Objects.hashCode(loader);
        hash = 31 * hash + java.util.Objects.hashCode(details);
        return hash;
    }
    @Override public String toString() { return "EvidenceMetadata[authority=" + authority + ", completeness=" + completeness + ", capturedAt=" + capturedAt + ", sourceId=" + sourceId + ", provenance=" + provenance + ", gameVersion=" + gameVersion + ", loader=" + loader + ", details=" + details + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<EvidenceMetadata> schema() {
            return new dev.openallay.value.ValueSchema<>(EvidenceMetadata.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<EvidenceMetadata>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(EvidenceMetadata.class, "authority", EvidenceMetadata::authority),
                    new dev.openallay.value.ValueSchema.Component<>(EvidenceMetadata.class, "completeness", EvidenceMetadata::completeness),
                    new dev.openallay.value.ValueSchema.Component<>(EvidenceMetadata.class, "capturedAt", EvidenceMetadata::capturedAt),
                    new dev.openallay.value.ValueSchema.Component<>(EvidenceMetadata.class, "sourceId", EvidenceMetadata::sourceId),
                    new dev.openallay.value.ValueSchema.Component<>(EvidenceMetadata.class, "provenance", EvidenceMetadata::provenance),
                    new dev.openallay.value.ValueSchema.Component<>(EvidenceMetadata.class, "gameVersion", EvidenceMetadata::gameVersion),
                    new dev.openallay.value.ValueSchema.Component<>(EvidenceMetadata.class, "loader", EvidenceMetadata::loader),
                    new dev.openallay.value.ValueSchema.Component<>(EvidenceMetadata.class, "details", EvidenceMetadata::details)), arguments -> new EvidenceMetadata(
                    (DataAuthority) arguments[0],
                    (DataCompleteness) arguments[1],
                    (Instant) arguments[2],
                    (String) arguments[3],
                    (String) arguments[4],
                    (String) arguments[5],
                    (String) arguments[6],
                    (Map) arguments[7]));
        }
    }
}
