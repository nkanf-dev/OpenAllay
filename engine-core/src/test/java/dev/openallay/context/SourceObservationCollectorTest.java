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

    @Test
    void tenThousandBuilderCaptureRangesRetainOneSourceAndTheActualObservedExtent() {
        SourceObservationCollector collector = new SourceObservationCollector();
        EvidenceMetadata first = builderEvidence(0, 2, Map.of());
        EvidenceMetadata latest = builderEvidence(9_999, 10_001, Map.of());
        collector.add(latest);
        for (int second = 9_998; second > 0; second--) {
            collector.add(builderEvidence(second, second + 2, Map.of()));
        }
        collector.add(first);

        List<SourceObservation> snapshot = collector.snapshot();
        assertEquals(1, snapshot.size());
        SourceObservation summary = snapshot.getFirst();
        assertSame(first, summary.evidence());
        assertEquals(at(0), summary.firstCapturedAt());
        assertEquals(at(2), summary.evidence().capturedAt());
        assertEquals(at(10_001), summary.lastCapturedAt());
        assertEquals(first.details(), summary.evidence().details());
        assertEquals(at(2).toString(), summary.evidence().details().get("openallay_builder:capture_end"));

        collector.addAll(snapshot);
        assertEquals(snapshot, collector.snapshot());
        collector.add(builderEvidence(10_000, 10_002, Map.of()));
        assertEquals(1, collector.snapshot().size());
        assertEquals(at(10_001), summary.lastCapturedAt());
        assertEquals(at(10_002), collector.snapshot().getFirst().lastCapturedAt());
    }

    @Test
    void builderExtentUsesActualStartNotCompletionOrArrivalOrder() {
        EvidenceMetadata earlyCompletion = builderEvidence(5, 6, Map.of());
        EvidenceMetadata earlyStart = builderEvidence(0, 20, Map.of());
        SourceObservationCollector collector = new SourceObservationCollector();
        collector.add(earlyCompletion);
        collector.add(new SourceObservation(builderEvidence(2, 10, Map.of()), at(30)));
        collector.add(earlyStart);

        SourceObservation summary = collector.snapshot().getFirst();
        assertSame(earlyStart, summary.evidence());
        assertEquals(at(0), summary.firstCapturedAt());
        assertEquals(at(20), summary.evidence().capturedAt());
        assertEquals(at(30), summary.lastCapturedAt());
        assertEquals(earlyStart.details(), summary.evidence().details());
    }

    @Test
    void builderRangesDoNotEraseAnyOtherDetailFromSourceIdentity() {
        SourceObservationCollector collector = new SourceObservationCollector();
        collector.add(builderEvidence(0, 1, Map.of()));
        collector.add(builderEvidence(1, 2, Map.of()));
        for (String key : List.of("openallay_builder:dimension", "openallay_builder:count",
                "openallay_builder:consistency", "openallay_builder:position",
                "openallay_builder:journal_coverage", "addon:capture_start", "addon:scope")) {
            collector.add(builderEvidence(2, 3, Map.of(key, "first")));
            collector.add(builderEvidence(3, 4, Map.of(key, "different")));
        }

        assertEquals(15, collector.snapshot().size());
    }

    @Test
    void incompleteInvalidOrInconsistentCaptureDetailsRemainIdentityNotInventedTime() {
        List<Map<String, String>> invalidRanges = List.of(
                Map.of("openallay_builder:capture_start", at(0).toString()),
                Map.of("openallay_builder:capture_end", at(2).toString()),
                Map.of("openallay_builder:capture_start", "not-an-instant",
                        "openallay_builder:capture_end", at(2).toString()),
                Map.of("openallay_builder:capture_start", at(0).toString(),
                        "openallay_builder:capture_end", "not-an-instant"),
                Map.of("openallay_builder:capture_start", at(3).toString(),
                        "openallay_builder:capture_end", at(2).toString()),
                Map.of("openallay_builder:capture_start", at(0).toString(),
                        "openallay_builder:capture_end", at(1).toString()));
        SourceObservationCollector collector = new SourceObservationCollector();
        collector.add(evidence(2, DataCompleteness.COMPLETE, Map.of()));
        for (Map<String, String> details : invalidRanges) {
            EvidenceMetadata metadata = evidence(2, DataCompleteness.COMPLETE, details);
            SourceObservation observation = new SourceObservation(metadata);
            assertEquals(at(2), observation.firstCapturedAt());
            assertEquals(at(2), observation.lastCapturedAt());
            collector.add(observation);
        }

        assertEquals(1 + invalidRanges.size(), collector.snapshot().size());
    }

    @Test
    void builderRangesStillKeepAuthorityCompletenessProvenanceVersionAndLoaderDistinct() {
        EvidenceMetadata base = builderEvidence(0, 1, Map.of());
        SourceObservationCollector collector = new SourceObservationCollector();
        collector.add(base);
        collector.add(new EvidenceMetadata(DataAuthority.CLIENT_VISIBLE, base.completeness(),
                base.capturedAt(), base.sourceId(), base.provenance(), base.gameVersion(), base.loader(), base.details()));
        collector.add(new EvidenceMetadata(base.authority(), DataCompleteness.PARTIAL,
                base.capturedAt(), base.sourceId(), base.provenance(), base.gameVersion(), base.loader(), base.details()));
        collector.add(new EvidenceMetadata(base.authority(), base.completeness(),
                base.capturedAt(), "test:other_source", base.provenance(), base.gameVersion(), base.loader(), base.details()));
        collector.add(new EvidenceMetadata(base.authority(), base.completeness(),
                base.capturedAt(), base.sourceId(), "test:other_provenance", base.gameVersion(), base.loader(), base.details()));
        collector.add(new EvidenceMetadata(base.authority(), base.completeness(),
                base.capturedAt(), base.sourceId(), base.provenance(), "other-version", base.loader(), base.details()));
        collector.add(new EvidenceMetadata(base.authority(), base.completeness(),
                base.capturedAt(), base.sourceId(), base.provenance(), base.gameVersion(), "other-loader", base.details()));

        assertEquals(7, collector.snapshot().size());
    }

    private static EvidenceMetadata builderEvidence(long start, long end, Map<String, String> extraDetails) {
        Map<String, String> details = new java.util.LinkedHashMap<>(extraDetails);
        details.put("openallay_builder:capture_start", at(start).toString());
        details.put("openallay_builder:capture_end", at(end).toString());
        return evidence(end, DataCompleteness.COMPLETE, details);
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
