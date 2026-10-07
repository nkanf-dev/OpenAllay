package dev.openallay.context;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Aggregates real captures without retaining a raw observation for every timestamp. */
public final class SourceObservationCollector {
    private final Map<SourceKey, SourceObservation> sources = new LinkedHashMap<>();

    public synchronized void add(EvidenceMetadata evidence) {
        add(new SourceObservation(evidence));
    }

    public synchronized void add(SourceObservation observation) {
        Objects.requireNonNull(observation, "observation");
        sources.merge(SourceKey.from(observation.evidence()), observation,
                SourceObservationCollector::merge);
    }

    public synchronized void addAll(Collection<SourceObservation> observations) {
        Objects.requireNonNull(observations, "observations").forEach(this::add);
    }

    public synchronized List<SourceObservation> snapshot() {
        return dev.openallay.util.Java8Collections.listCopyOf(sources.values());
    }

    private static SourceObservation merge(SourceObservation left, SourceObservation right) {
        int startOrder = right.firstCapturedAt().compareTo(left.firstCapturedAt());
        EvidenceMetadata first = startOrder < 0
                || (startOrder == 0 && right.evidence().capturedAt().isBefore(left.evidence().capturedAt()))
                ? right.evidence()
                : left.evidence();
        Instant last = right.lastCapturedAt().isAfter(left.lastCapturedAt())
                ? right.lastCapturedAt()
                : left.lastCapturedAt();
        return new SourceObservation(first, last);
    }

    private static final class SourceKey {
        private final DataAuthority authority;
        private final DataCompleteness completeness;
        private final String sourceId;
        private final String provenance;
        private final String gameVersion;
        private final String loader;
        private final Map<String, String> details;
        private SourceKey(DataAuthority authority, DataCompleteness completeness, String sourceId, String provenance, String gameVersion, String loader, Map<String, String> details) {
            this.authority = authority;
            this.completeness = completeness;
            this.sourceId = sourceId;
            this.provenance = provenance;
            this.gameVersion = gameVersion;
            this.loader = loader;
            this.details = details;
        }

        private static SourceKey from(EvidenceMetadata evidence) {
            return new SourceKey(
                    evidence.authority(),
                    evidence.completeness(),
                    evidence.sourceId(),
                    evidence.provenance(),
                    evidence.gameVersion(),
                    evidence.loader(),
                    SourceObservation.identityDetails(evidence));
        }
            @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SourceKey)) return false;
            SourceKey that = (SourceKey) other;
            return java.util.Objects.equals(authority, that.authority) && java.util.Objects.equals(completeness, that.completeness) && java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(provenance, that.provenance) && java.util.Objects.equals(gameVersion, that.gameVersion) && java.util.Objects.equals(loader, that.loader) && java.util.Objects.equals(details, that.details);
        }
        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(authority);
            hash = 31 * hash + java.util.Objects.hashCode(completeness);
            hash = 31 * hash + java.util.Objects.hashCode(sourceId);
            hash = 31 * hash + java.util.Objects.hashCode(provenance);
            hash = 31 * hash + java.util.Objects.hashCode(gameVersion);
            hash = 31 * hash + java.util.Objects.hashCode(loader);
            hash = 31 * hash + java.util.Objects.hashCode(details);
            return hash;
        }
    }
}
