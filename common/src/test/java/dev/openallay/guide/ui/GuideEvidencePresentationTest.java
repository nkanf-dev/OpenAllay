package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.guide.GuideSource;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class GuideEvidencePresentationTest {
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
