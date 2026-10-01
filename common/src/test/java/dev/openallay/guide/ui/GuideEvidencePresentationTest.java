package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.guide.GuideSource;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class GuideEvidencePresentationTest {
    @Test
    void thousandsOfPositionAndTimeObservationsBecomeOneLosslessGroup() {
        List<GuideSource> sources = new ArrayList<>();
        for (int index = 0; index < 4096; index++) {
            sources.add(new GuideSource("openallay:run_javascript", new EvidenceMetadata(
                    DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.COMPLETE,
                    Instant.EPOCH.plusSeconds(index), "openallay_builder:read", "openallay:builder",
                    "26.2", "fabric", Map.of("openallay_builder:dimension", "minecraft:overworld",
                            "openallay_builder:position", index + ",64,0",
                            "openallay_builder:count", "1")), Instant.EPOCH.plusSeconds(index + 1)));
        }
        List<GuideEvidencePresentation.Group> groups = GuideEvidencePresentation.groups(sources);
        assertEquals(1, groups.size());
        var group = groups.getFirst();
        assertEquals(Instant.EPOCH, group.firstCapturedAt());
        assertEquals(Instant.EPOCH.plusSeconds(4096), group.lastCapturedAt());
        assertEquals(sources, group.records());
        assertEquals("4095,64,0", group.records().getLast().evidence().details().get("openallay_builder:position"));
        sources.clear();
        assertEquals(4096, group.records().size());
        assertThrows(UnsupportedOperationException.class, () -> group.records().clear());
        assertEquals(Map.of("openallay_builder:dimension", "minecraft:overworld"), group.identity().scope());
    }

    @Test
    void observationRangeUsesRecordedExtremaRatherThanArrivalOrder() {
        EvidenceMetadata later = new EvidenceMetadata(DataAuthority.CLIENT_VISIBLE, DataCompleteness.COMPLETE,
                Instant.EPOCH.plusSeconds(10), "minecraft:client_blocks", "minecraft:captured",
                "26.2", "fabric", Map.of());
        EvidenceMetadata earlier = new EvidenceMetadata(later.authority(), later.completeness(),
                Instant.EPOCH, later.sourceId(), later.provenance(), later.gameVersion(), later.loader(), Map.of());
        var records = List.of(new GuideSource("openallay:run_javascript", later, Instant.EPOCH.plusSeconds(30)),
                new GuideSource("openallay:run_javascript", earlier, Instant.EPOCH.plusSeconds(20)));
        var group = GuideEvidencePresentation.groups(records).getFirst();
        assertEquals(Instant.EPOCH, group.firstCapturedAt());
        assertEquals(Instant.EPOCH.plusSeconds(30), group.lastCapturedAt());
        assertEquals(records, group.records());
    }

    @Test
    void compactBuilderSummaryShowsTheTrueCaptureStartWithoutChangingItsEvidence() {
        Instant start = Instant.EPOCH;
        Instant completed = Instant.EPOCH.plusSeconds(2);
        Instant lastCompleted = Instant.EPOCH.plusSeconds(10_001);
        EvidenceMetadata evidence = new EvidenceMetadata(
                DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.COMPLETE, completed,
                "openallay_builder:read-region", "openallay:builder", "26.2", "fabric",
                Map.of("openallay_builder:capture_start", start.toString(),
                        "openallay_builder:capture_end", completed.toString(),
                        "openallay_builder:dimension", "minecraft:overworld",
                        "openallay_builder:consistency", "multi-slice-non-atomic"));
        GuideSource summary = new GuideSource("openallay:run_javascript", evidence, lastCompleted);

        var group = GuideEvidencePresentation.groups(List.of(summary)).getFirst();
        assertEquals(start, group.firstCapturedAt());
        assertEquals(lastCompleted, group.lastCapturedAt());
        assertEquals(List.of(summary), group.records());
        assertEquals(completed, group.records().getFirst().evidence().capturedAt());
        assertEquals(evidence.details(), group.records().getFirst().evidence().details());
        assertEquals(Map.of("openallay_builder:dimension", "minecraft:overworld",
                "openallay_builder:consistency", "multi-slice-non-atomic"), group.identity().scope());
    }

    @Test
    void invalidBuilderCaptureDetailsDoNotInventAnEarlierUiRange() {
        Instant completed = Instant.EPOCH.plusSeconds(2);
        EvidenceMetadata evidence = new EvidenceMetadata(
                DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.COMPLETE, completed,
                "openallay_builder:read-region", "openallay:builder", "26.2", "fabric",
                Map.of("openallay_builder:capture_start", Instant.EPOCH.toString(),
                        "openallay_builder:capture_end", "not-an-instant"));
        GuideSource source = new GuideSource("openallay:run_javascript", evidence);

        var group = GuideEvidencePresentation.groups(List.of(source)).getFirst();
        assertEquals(completed, group.firstCapturedAt());
        assertEquals(completed, group.lastCapturedAt());
        assertEquals(evidence.details(), group.records().getFirst().evidence().details());
        assertEquals(evidence.details(), group.identity().scope());
        GuideSource withoutRange = new GuideSource("openallay:run_javascript", new EvidenceMetadata(
                evidence.authority(), evidence.completeness(), completed, evidence.sourceId(),
                evidence.provenance(), evidence.gameVersion(), evidence.loader(), Map.of()));
        assertEquals(2, GuideEvidencePresentation.groups(List.of(source, withoutRange)).size());
    }

    @Test
    void groupingPreservesAuthorityCoverageProvenanceVersionLoaderAndScopeDifferences() {
        EvidenceMetadata base = new EvidenceMetadata(DataAuthority.CLIENT_VISIBLE, DataCompleteness.COMPLETE,
                Instant.EPOCH, "minecraft:client_blocks", "minecraft:captured", "26.2", "fabric",
                Map.of("minecraft:dimension", "minecraft:overworld", "openallay:scope", "world"));
        List<GuideSource> sources = new ArrayList<>();
        sources.add(new GuideSource("openallay:run_javascript", base));
        sources.add(new GuideSource("other:tool", base));
        sources.add(source(base, DataAuthority.SERVER_AUTHORITATIVE, base.completeness(), base.sourceId(),
                base.provenance(), base.gameVersion(), base.loader(), base.details()));
        sources.add(source(base, base.authority(), DataCompleteness.PARTIAL, base.sourceId(),
                base.provenance(), base.gameVersion(), base.loader(), base.details()));
        sources.add(source(base, base.authority(), base.completeness(), "minecraft:server_blocks",
                base.provenance(), base.gameVersion(), base.loader(), base.details()));
        sources.add(source(base, base.authority(), base.completeness(), base.sourceId(),
                "minecraft:another_capture", base.gameVersion(), base.loader(), base.details()));
        sources.add(source(base, base.authority(), base.completeness(), base.sourceId(),
                base.provenance(), "26.3", base.loader(), base.details()));
        sources.add(source(base, base.authority(), base.completeness(), base.sourceId(),
                base.provenance(), base.gameVersion(), "neoforge", base.details()));
        sources.add(source(base, base.authority(), base.completeness(), base.sourceId(),
                base.provenance(), base.gameVersion(), base.loader(),
                Map.of("minecraft:dimension", "minecraft:the_nether", "openallay:scope", "world")));
        sources.add(source(base, base.authority(), base.completeness(), base.sourceId(),
                base.provenance(), base.gameVersion(), base.loader(),
                Map.of("minecraft:dimension", "minecraft:overworld", "openallay:scope", "different")));
        sources.add(source(base, base.authority(), base.completeness(), base.sourceId(),
                base.provenance(), base.gameVersion(), base.loader(), Map.of("addon:count", "12")));
        sources.add(source(base, base.authority(), base.completeness(), base.sourceId(),
                base.provenance(), base.gameVersion(), base.loader(), Map.of("addon:count", "13")));
        var groups = GuideEvidencePresentation.groups(sources);
        assertEquals(sources.size(), groups.size());
        assertTrue(groups.stream().allMatch(group -> group.records().size() == 1));
        assertEquals(List.of(), GuideEvidencePresentation.groups(List.of()));
    }

    private static GuideSource source(
            EvidenceMetadata base, DataAuthority authority, DataCompleteness completeness,
            String sourceId, String provenance, String version, String loader, Map<String, String> details) {
        return new GuideSource("openallay:run_javascript", new EvidenceMetadata(authority, completeness,
                base.capturedAt(), sourceId, provenance, version, loader, details));
    }

    @Test
    void normalEvidenceUsesClosedHumanLabelsAndNoTechnicalOrPrivateData() {
        Instant captured = Instant.parse("2026-10-01T14:35:00Z");
        for (DataAuthority authority : DataAuthority.values()) {
            for (DataCompleteness completeness : DataCompleteness.values()) {
                GuideSource source = new GuideSource("private:tool", new EvidenceMetadata(
                        authority, completeness, captured, "private:unknown_source", "private:raw_provenance",
                        "26.2", "private-loader", Map.of("openallay:test_credential", "secret-value",
                                "openallay:test_history_scope", "private-history-scope",
                                "openallay:test_detail", "private-transcript")));
                GuideEvidencePresentation normal = GuideEvidencePresentation.from(source);
                assertEquals("screen.openallay.evidence.source.unknown", normal.sourceKey());
                assertEquals("screen.openallay.detail.tool.coverage."
                        + completeness.name().toLowerCase(java.util.Locale.ROOT), normal.coverageKey());
                assertEquals(captured, normal.capturedAt());
                for (String forbidden : new String[] {"private:", "private-loader", "secret-value",
                        "raw_provenance", "private-history-scope", "private-transcript", "CLIENT_VISIBLE"}) {
                    assertFalse(normal.toString().contains(forbidden));
                }
                assertEquals("private:unknown_source", source.evidence().sourceId());
                assertEquals("private:raw_provenance", source.evidence().provenance());
            }
        }
    }

    @Test
    void recipeBookSourceKeepsClientVisibleMeaningSeparateFromCoverage() {
        GuideEvidencePresentation view = GuideEvidencePresentation.from(new GuideSource(
                "openallay:run_javascript", new EvidenceMetadata(DataAuthority.CLIENT_VISIBLE,
                        DataCompleteness.PARTIAL, Instant.EPOCH, "minecraft:client_recipe_book",
                        "minecraft:captured", "26.2", "fabric", Map.of())));
        assertEquals("screen.openallay.evidence.source.recipe_book", view.sourceKey());
        assertEquals("screen.openallay.evidence.authority.client_visible", view.authorityKey());
        assertEquals("screen.openallay.detail.tool.coverage.partial", view.coverageKey());
        for (String sourceId : new String[] {"minecraft:recipe_manager", "openallay:recipe_catalog"}) {
            GuideEvidencePresentation recipes = GuideEvidencePresentation.from(new GuideSource(
                    "openallay:run_javascript", new EvidenceMetadata(DataAuthority.SERVER_AUTHORITATIVE,
                            DataCompleteness.COMPLETE, Instant.EPOCH, sourceId, "minecraft:captured",
                            "26.2", "fabric", Map.of())));
            assertEquals("screen.openallay.evidence.source.recipes", recipes.sourceKey());
        }
    }

    @Test
    void builderReadbackUsesKnownOperationSourceAndActualEvidenceAuthority() {
        for (String sourceId : new String[] {"openallay_builder:write-readback", "openallay_builder:undo-readback"}) {
            GuideSource source = new GuideSource("openallay:run_javascript", new EvidenceMetadata(
                    DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.COMPLETE,
                    Instant.parse("2026-10-01T14:35:00Z"), sourceId, "openallay_builder:native",
                    "26.2", "fabric", Map.of()));
            GuideEvidencePresentation view = GuideEvidencePresentation.from(source);
            assertEquals("screen.openallay.evidence.source.builder", view.sourceKey());
            assertEquals("screen.openallay.evidence.authority.server_authoritative", view.authorityKey());
            assertEquals("screen.openallay.detail.tool.coverage.complete", view.coverageKey());
            assertEquals(source.evidence().capturedAt(), view.capturedAt());
            assertFalse(view.toString().contains(sourceId));
        }
    }

    @Test
    void serverAuthorityIsNotInferredFromTheSourceName() {
        GuideEvidencePresentation view = GuideEvidencePresentation.from(new GuideSource(
                "openallay:run_javascript", new EvidenceMetadata(DataAuthority.SERVER_AUTHORITATIVE,
                        DataCompleteness.UNKNOWN, Instant.EPOCH, "minecraft:world_observation",
                        "minecraft:captured", "26.2", "fabric", Map.of())));
        assertEquals("screen.openallay.evidence.authority.server_authoritative", view.authorityKey());
        assertEquals("screen.openallay.detail.tool.coverage.unknown", view.coverageKey());
    }
}
