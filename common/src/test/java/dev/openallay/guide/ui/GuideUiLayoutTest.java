package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GuideUiLayoutTest {
    @Test
    void supportedGuiScalesKeepEveryActionInsideItsRegion() {
        for (int[] size : new int[][] {{569, 320}, {427, 320}, {320, 240}, {240, 180}, {900, 500}}) {
            for (boolean detail : new boolean[] {false, true}) {
                GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], detail);
                assertTrue(layout.transcript().height() >= 0);
                for (GuideUiLayout.Rect control : layout.header().controls()) {
                    assertInside(layout.topBar(), control);
                    assertFalse(overlap(layout.header().title(), control));
                    for (GuideUiLayout.Rect other : layout.header().controls()) {
                        if (control != other) assertFalse(overlap(control, other));
                    }
                }
                assertInside(layout.topBar(), layout.header().status());
                assertInside(layout.composer(), layout.composerControls().input());
                assertInside(layout.composer(), layout.composerControls().send());
                assertInside(layout.composer(), layout.composerControls().stop());
                assertInside(layout.composer(), layout.composerControls().retry());
                assertTrue(layout.composer().bottom() <= size[1]);
                assertTrue(layout.progress().bottom() <= layout.composer().y());
                assertTrue(layout.transcript().bottom() <= layout.progress().y());
                if (detail) assertTrue(layout.detail().bottom() <= layout.progress().y());
            }
        }
    }

    @Test
    void localizedLabelsWrapControlsRatherThanOverlapTitle() {
        GuideUiLayout layout = GuideUiLayout.calculate(427, 320, false, 150, 72, 60, 66, true);
        assertTrue(layout.header().sessions().y() > layout.header().title().y());
        for (GuideUiLayout.Rect control : layout.header().controls()) assertInside(layout.topBar(), control);
        GuideUiLayout withoutSettings = GuideUiLayout.calculate(569, 320, false, 140, 60, 48, 54, false);
        assertTrue(withoutSettings.header().settings().width() == 0);
    }

    private static void assertInside(GuideUiLayout.Rect outer, GuideUiLayout.Rect inner) {
        assertTrue(inner.x() >= outer.x());
        assertTrue(inner.y() >= outer.y());
        assertTrue(inner.right() <= outer.right());
        assertTrue(inner.bottom() <= outer.bottom());
    }

    private static boolean overlap(GuideUiLayout.Rect a, GuideUiLayout.Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }

    @Test
    void normalLayoutKeepsRailAndInlineDetailOutsideTranscript() {
        GuideUiLayout layout = GuideUiLayout.calculate(900, 500, true);
        assertFalse(layout.narrow());
        assertFalse(layout.detailOverlay());
        assertTrue(layout.sessionRail().width() > 0);
        assertTrue(layout.transcript().x() > layout.sessionRail().x() + layout.sessionRail().width());
        assertTrue(layout.detail().x() >= layout.transcript().x() + layout.transcript().width());
        assertTrue(layout.progress().height() > 0);
        assertTrue(layout.transcript().y() + layout.transcript().height() <= layout.progress().y());
        assertTrue(layout.progress().y() + layout.progress().height() <= layout.composer().y());
    }

    @Test
    void narrowLayoutUsesOverlayWithoutShrinkingTranscriptToNothing() {
        GuideUiLayout layout = GuideUiLayout.calculate(320, 240, true);
        assertTrue(layout.narrow());
        assertTrue(layout.detailOverlay());
        assertTrue(layout.sessionRail().width() == 0);
        assertTrue(layout.transcript().width() > 250);
        assertTrue(layout.detail().width() <= 320 - 16);
        assertTrue(layout.progress().width() == layout.composer().width());
    }
}
