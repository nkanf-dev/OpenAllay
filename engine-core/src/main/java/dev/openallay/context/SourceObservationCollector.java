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
        return List.copyOf(sources.values());
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

    private record SourceKey(
            DataAuthority authority,
            DataCompleteness completeness,
            String sourceId,
            String provenance,
            String gameVersion,
            String loader,
            Map<String, String> details) {
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
    }
}
