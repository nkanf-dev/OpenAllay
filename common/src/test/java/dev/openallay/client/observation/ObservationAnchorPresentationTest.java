package dev.openallay.client.observation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class ObservationAnchorPresentationTest {
    @Test void labelsComeFromOneFrozenNativeSampleNotASelectedTaskMode() {
        var anchor = ObservationUiFixture.anchor(0, false, true);
        var chips = ObservationAnchorPresentation.chips(anchor);
        assertEquals("screen.openallay.observation.slot", chips.get(0).key());
        assertEquals("8 · Stick ×2", chips.get(0).value());
        assertTrue(chips.stream().anyMatch(value -> value.key().equals("screen.openallay.observation.crosshair")
                && value.value().equals("minecraft:oak_log · 12,65,-4")));
        assertTrue(chips.stream().anyMatch(value -> value.key().equals("screen.openallay.observation.held")
                && value.value().equals("Stick ×2")));
        assertEquals(anchor.focus().target().block().position().x(), 12);
    }
}
