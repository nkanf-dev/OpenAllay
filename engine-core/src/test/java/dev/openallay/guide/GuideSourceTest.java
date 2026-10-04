package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.testing.GroundedTestFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class GuideSourceTest {
    @Test
    void twoArgumentSourceRepresentsOneRealCapture() {
        EvidenceMetadata evidence = GroundedTestFixtures.serverEvidence();
        GuideSource source = new GuideSource("openallay:get_recipe", evidence);

        assertSame(evidence, source.evidence());
        assertEquals(evidence.capturedAt(), source.lastCapturedAt());

    }

    @Test
    void summaryKeepsTheOriginalEvidenceAndActualTimeExtent() {
        EvidenceMetadata evidence = GroundedTestFixtures.serverEvidence();
        GuideSource source = new GuideSource(
                "openallay:run_javascript", evidence, Instant.EPOCH.plusSeconds(30));

        assertSame(evidence, source.evidence());
        assertEquals(Instant.EPOCH.plusSeconds(30), source.lastCapturedAt());

    }

    @Test
    void rejectsMissingToolAndImpossibleObservationSummary() {
        EvidenceMetadata evidence = GroundedTestFixtures.serverEvidence();
        assertThrows(IllegalArgumentException.class, () -> new GuideSource(" ", evidence));
        assertThrows(IllegalArgumentException.class, () -> new GuideSource(null, evidence));
        assertThrows(IllegalArgumentException.class, () -> new GuideSource(
                "openallay:get_recipe", evidence, Instant.EPOCH.minusSeconds(1)));
    }
}
