package dev.openallay.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SourceObservationCollectorTest {
    @Test
    void thousandsOfTimestampsRetainOneSummaryAndTheActualFirstCapture() {
        SourceObservationCollector collector = new SourceObservationCollector();
        EvidenceMetadata first = evidence(0, DataCompleteness.COMPLETE, Map.of());
        EvidenceMetadata latest = evidence(4_999, DataCompleteness.COMPLETE, Map.of());
        collector.add(latest);
        for (int second = 4_998; second > 0; second--) {
            collector.add(evidence(second, DataCompleteness.COMPLETE, Map.of()));
        }
        collector.add(first);

        List<SourceObservation> snapshot = collector.snapshot();
        assertEquals(1, snapshot.size());
        assertSame(first, snapshot.getFirst().evidence());
        assertEquals(latest.capturedAt(), snapshot.getFirst().lastCapturedAt());

        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());

        collector.add(evidence(5_000, DataCompleteness.COMPLETE, Map.of()));

        assertEquals(latest.capturedAt(), snapshot.getFirst().lastCapturedAt());
        assertEquals(at(5_000), collector.snapshot().getFirst().lastCapturedAt());
    }

    @Test
    void mergesSummariesWithoutLosingTheirActualTimeExtent() {
        EvidenceMetadata first = evidence(1, DataCompleteness.COMPLETE, Map.of());
        SourceObservationCollector collector = new SourceObservationCollector();
        collector.add(new SourceObservation(
                evidence(4, DataCompleteness.COMPLETE, Map.of()), at(20)));
        collector.addAll(List.of(new SourceObservation(first, at(30))));
        collector.add(new SourceObservation(
                evidence(2, DataCompleteness.COMPLETE, Map.of()), at(10)));

        assertEquals(List.of(new SourceObservation(first, at(30))), collector.snapshot());
        assertSame(first, collector.snapshot().getFirst().evidence());
    }

    @Test
    void keepsCompletenessAndUnknownOrDifferentCoverageDetailsSeparate() {
        SourceObservationCollector collector = new SourceObservationCollector();
        collector.add(evidence(0, DataCompleteness.COMPLETE, Map.of()));
        collector.add(evidence(1, DataCompleteness.PARTIAL, Map.of()));
        collector.add(evidence(2, DataCompleteness.UNKNOWN, Map.of()));
        collector.add(evidence(3, DataCompleteness.COMPLETE, Map.of("test:coverage", "unknown")));
        collector.add(evidence(4, DataCompleteness.COMPLETE, Map.of("test:coverage", "loaded_chunks")));
        collector.add(evidence(5, DataCompleteness.COMPLETE, Map.of("test:coverage", "full_world")));

        assertEquals(6, collector.snapshot().size());
    }

    @Test
    void everyNonTimestampMetadataFieldParticipatesInTheKey() {
        SourceObservationCollector collector = new SourceObservationCollector();
        collector.add(evidence(0, DataCompleteness.COMPLETE, Map.of()));
        collector.add(new EvidenceMetadata(
                DataAuthority.CLIENT_VISIBLE, DataCompleteness.COMPLETE, at(1),
                "test:source", "test:provenance", "test", "test-loader", Map.of()));
        collector.add(new EvidenceMetadata(
                DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.COMPLETE, at(2),
                "test:other_source", "test:provenance", "test", "test-loader", Map.of()));
        collector.add(new EvidenceMetadata(
                DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.COMPLETE, at(3),
                "test:source", "test:other_provenance", "test", "test-loader", Map.of()));
        collector.add(new EvidenceMetadata(
                DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.COMPLETE, at(4),
                "test:source", "test:provenance", "other-version", "test-loader", Map.of()));
        collector.add(new EvidenceMetadata(
                DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.COMPLETE, at(5),
                "test:source", "test:provenance", "test", "other-loader", Map.of()));

        assertEquals(6, collector.snapshot().size());
    }

    @Test
    void reusingTheSameSourceDoesNotCreateMoreMetadata() {
        EvidenceMetadata evidence = evidence(0, DataCompleteness.COMPLETE, Map.of());
        SourceObservationCollector collector = new SourceObservationCollector();
        collector.add(evidence);
        collector.add(evidence);

        assertEquals(List.of(new SourceObservation(evidence, at(0))), collector.snapshot());
    }

    @Test
    void overlappingInheritedSummariesRemainOneOriginWithoutAClaimedCaptureCount() {
        EvidenceMetadata first = evidence(0, DataCompleteness.COMPLETE, Map.of());
        SourceObservation original = new SourceObservation(first, at(20));
        SourceObservation derived = new SourceObservation(first, at(20));
        SourceObservationCollector collector = new SourceObservationCollector();
        collector.add(original);
        collector.add(derived);
        collector.addAll(collector.snapshot());
        assertEquals(List.of(original), collector.snapshot());
    }

    @Test
    void rejectsMissingEvidenceAndImpossibleTimeExtent() {
        EvidenceMetadata evidence = evidence(2, DataCompleteness.COMPLETE, Map.of());
        assertThrows(NullPointerException.class, () -> new SourceObservation(null, at(2)));
        assertThrows(NullPointerException.class, () -> new SourceObservation(evidence, null));
        assertThrows(IllegalArgumentException.class, () -> new SourceObservation(evidence, at(1)));
        assertEquals(new SourceObservation(evidence, at(2)), new SourceObservation(evidence));
    }

    private static EvidenceMetadata evidence(
            long seconds, DataCompleteness completeness, Map<String, String> details) {
        return new EvidenceMetadata(
                DataAuthority.SERVER_AUTHORITATIVE, completeness, at(seconds),
                "test:source", "test:provenance", "test", "test-loader", details);
    }

    private static Instant at(long seconds) {
        return Instant.EPOCH.plusSeconds(seconds);
    }
}
