package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class GuideUiLayoutTest {
    @Test
    void supportedGuiScalesKeepEveryVisibleActionInsideItsRegion() {
        for (int[] size : new int[][] {{569, 320}, {427, 320}, {320, 240}, {240, 180}, {900, 500}}) {
            for (boolean detail : new boolean[] {false, true}) {
                for (boolean active : new boolean[] {false, true}) {
                    GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], detail,
                            120, 60, 48, 54, true, false, active, 0);
                    assertReadableTranscript(layout);
                    for (GuideUiLayout.Rect control : layout.header().controls()) {
                        assertInside(layout.topBar(), control);
                        assertFalse(overlap(layout.header().title(), control));
                        for (GuideUiLayout.Rect other : layout.header().controls()) {
                            if (control != other) assertFalse(overlap(control, other));
                        }
                    }
                    assertInside(layout.topBar(), layout.header().title());
                    assertInside(layout.topBar(), layout.header().status());
                    assertInside(layout.composer(), layout.composerControls().input());
                    assertInside(layout.composer(), layout.composerControls().send());
                    if (active) assertInside(layout.composer(), layout.composerControls().stop());
                    else assertEquals(GuideUiLayout.Rect.EMPTY, layout.composerControls().stop());
                    assertEquals(GuideUiLayout.Rect.EMPTY, layout.composerControls().retry());
                    assertFalse(overlap(layout.composerControls().input(), layout.composerControls().send()));
                    assertFalse(overlap(layout.composerControls().send(), layout.composerControls().stop()));
                    assertTrue(layout.composer().bottom() <= size[1]);
                    assertTrue(layout.progress().bottom() <= layout.composerNotice().y());
                    assertTrue(layout.composerNotice().bottom() <= layout.composer().y());
                    assertTrue(layout.transcript().bottom() <= layout.progress().y());
                    if (detail) assertTrue(layout.detail().bottom() <= layout.progress().y());
                }
            }
        }
    }

    @Test
    void secondaryHeaderActionsStayInOverflowWhileTheNameOwnsItsBudget() {
        for (int[] size : new int[][] {{240, 180}, {320, 240}, {427, 320}, {900, 500}}) {
            GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], false,
                    220, 180, 160, 180, true);
            GuideUiLayout.Header header = layout.header();
            assertTrue(layout.topBar().height() <= 40);
            assertEquals(220, header.title().width());
            assertEquals(GuideUiLayout.Rect.EMPTY, header.create());
            assertEquals(GuideUiLayout.Rect.EMPTY, header.delete());
            assertEquals(GuideUiLayout.Rect.EMPTY, header.export());
            assertEquals(GuideUiLayout.Rect.EMPTY, header.refresh());
            assertEquals(4, header.controls().size());
            for (GuideUiLayout.Rect control : header.controls()) {
                assertTrue(control.width() > 0 && control.height() > 0);
                assertEquals(header.sessions().y(), control.y());
                assertInside(layout.topBar(), control);
                assertFalse(overlap(header.title(), control));
            }
        }
        GuideUiLayout compact = GuideUiLayout.calculate(240, 180, false);
        assertEquals(120, compact.header().title().width());
        assertTrue(GuideUiLayout.calculate(900, 500, false).header().title().width() > 0);
    }

    @Test
    void telemetryRestoresTheFullLowerLeftCardAndFallsBackWithoutCoveringContent() {
        for (int[] size : new int[][] {{240, 180}, {280, 180}, {280, 240}, {320, 240}, {559, 320},
                {560, 180}, {560, 240}, {560, 320}, {900, 500}}) {
            for (boolean showRail : new boolean[] {false, true}) {
                for (boolean active : new boolean[] {false, true}) {
                    GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], true,
                            120, 60, 48, 54, true, true, active, 4, showRail);
                    GuideUiLayout.Rect telemetry = layout.telemetry();
                    assertInside(new GuideUiLayout.Rect(0, 0, size[0], size[1]), telemetry);
                    boolean card = size[0] >= 760 && size[1] >= 240 && showRail;
                    assertEquals(card, layout.telemetryCard());
                    assertEquals(card ? 80 : 14, telemetry.height());
                    if (card) {
                        assertEquals(layout.sessionRail().x(), telemetry.x());
                        assertEquals(layout.sessionRail().width(), telemetry.width());
                        assertEquals(layout.composer().bottom(), telemetry.bottom());
                        assertTrue(layout.sessionRail().bottom() + 2 <= telemetry.y());
                        assertTrue(telemetry.right() < layout.transcript().x());
                    } else {
                        assertEquals(layout.composer().right(), telemetry.right());
                        assertEquals(Math.min(180, layout.composer().width()), telemetry.width());
                        assertTrue(telemetry.width() < layout.composer().width());
                        assertTrue(telemetry.y() >= layout.composer().bottom());
                    }
                    for (GuideUiLayout.Rect region : List.of(layout.topBar(), layout.composer(), layout.progress(),
                            layout.composerNotice(), layout.transcript(), layout.sessionRail(), layout.detail())) {
                        assertFalse(overlap(telemetry, region));
                    }
                    assertReadableTranscript(layout);
                }
            }
        }
    }

    @Test
    void normalWideIdleAndActiveScreensKeepAllThreeTelemetryRowsBelowTheRail() {
        for (int[] size : new int[][] {{560, 240}, {569, 320}, {900, 500}}) {
            for (boolean active : new boolean[] {false, true}) {
                GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], false,
                        130, 60, 48, 54, true, true, active, 4);
                GuideUiLayout.Rect card = layout.telemetry();
                assertTrue(layout.telemetryCard());
                assertEquals(80, card.height());
                assertTrue(card.y() + 7 + 55 + 9 <= card.bottom(), "cost row retains native 9px text height");
                assertEquals(layout.topBar().x(), card.x());
                assertEquals(128, card.width());
                assertTrue(card.x() < layout.composer().x());
                assertTrue(layout.sessionRail().bottom() + 2 <= card.y());
                for (GuideUiLayout.Rect region : List.of(layout.transcript(), layout.composer(), layout.composerNotice(),
                        layout.progress(), layout.sessionRail())) assertFalse(overlap(card, region));
                assertReadableTranscript(layout);
            }
        }
    }

    @Test
    void imageAndPendingDraftRowsPreserveFourReadableLinesAndActualInput() {
        for (int[] size : new int[][] {{240, 180}, {280, 180}, {320, 240}, {427, 320}, {569, 320}, {900, 500}}) {
            for (boolean images : new boolean[] {false, true}) {
                for (boolean active : new boolean[] {false, true}) {
                    for (int pending : new int[] {0, 1, 4}) {
                        GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], true,
                                120, 60, 48, 54, true, images, active, pending);
                        GuideUiLayout.ComposerExtras extras = layout.composerExtras(images, active, pending);
                        assertReadableTranscript(layout);
                        assertInside(layout.composerControls().input(), extras.input());
                        assertTrue(extras.input().height() >= 24);
                        if (images) assertTrue(extras.images().height() >= 12);
                        else assertEquals(GuideUiLayout.Rect.EMPTY, extras.images());
                        if (active || pending > 0) assertTrue(extras.footer().height() >= 12);
                        else assertEquals(GuideUiLayout.Rect.EMPTY, extras.footer());
                        for (GuideUiLayout.Rect row : List.of(extras.images(), extras.footer(), extras.pending())) {
                            if (row.height() == 0) continue;
                            assertInside(layout.composerControls().input(), row);
                            assertFalse(overlap(extras.input(), row));
                            assertFalse(overlap(layout.telemetry(), row));
                        }
                        assertFalse(overlap(extras.images(), extras.footer()));
                        assertFalse(overlap(extras.images(), extras.pending()));
                        assertFalse(overlap(extras.footer(), extras.pending()));
                        assertInside(new GuideUiLayout.Rect(0, 0, size[0], size[1]), layout.telemetry());
                        assertFalse(overlap(layout.telemetry(), layout.composer()));
                    }
                }
            }
        }
        GuideUiLayout shortest = GuideUiLayout.calculate(240, 180, false,
                120, 60, 48, 54, true, true, true, 4);
        assertEquals(0, shortest.composerExtras(true, true, 4).pending().height());
        assertTrue(shortest.composer().height() >= 52 && shortest.composer().height() <= 68);
    }

    @Test
    void localizedLabelsStayInOneRowRatherThanOverlapTitle() {
        GuideUiLayout layout = GuideUiLayout.calculate(427, 320, false, 150, 72, 60, 66, true);
        assertTrue(layout.header().title().width() > 0);
        for (GuideUiLayout.Rect control : layout.header().controls()) {
            assertEquals(layout.header().sessions().y(), control.y());
            assertInside(layout.topBar(), control);
            assertFalse(overlap(layout.header().title(), control));
        }
        GuideUiLayout withoutSettings = GuideUiLayout.calculate(569, 320, false, 140, 60, 48, 54, false);
        assertEquals(GuideUiLayout.Rect.EMPTY, withoutSettings.header().settings());
        assertEquals(3, withoutSettings.header().controls().size());
        assertTrue(withoutSettings.header().overflow().width() > 0);
    }

    @Test
    void styledNamesTakePriorityAtEveryScaleWithoutStealingFourTranscriptLines() {
        // Fixture widths are explicit fake-font inputs, not measurements of a screenshot.
        for (int[] size : new int[][] {{240, 180}, {320, 240}, {427, 320}, {900, 500}}) {
            for (int titleWidth : new int[] {120, 130, 156, 220, 900}) {
                for (int sessionsWidth : new int[] {36, 60, 180}) {
                    for (boolean settings : new boolean[] {false, true}) {
                        for (boolean images : new boolean[] {false, true}) {
                            for (boolean active : new boolean[] {false, true}) {
                                for (int pending : new int[] {0, 4}) {
                                    GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], true,
                                            titleWidth, sessionsWidth, 48, 54, settings, images, active, pending);
                                    GuideUiLayout.Header header = layout.header();
                                    assertTrue(header.title().width() > 0, "names never silently disappear");
                                    assertTrue(header.height() == 24 || header.height() == 40);
                                    assertEquals(header.height(), layout.topBar().height());
                                    assertInside(layout.topBar(), header.title());
                                    // A 120px normal label with a 10px bold advance must remain complete.
                                    if (titleWidth <= 130) assertEquals(titleWidth, header.title().width());
                                    if (titleWidth == 220 && size[1] >= 240) assertEquals(titleWidth, header.title().width());
                                    for (GuideUiLayout.Rect control : header.controls()) {
                                        assertTrue(control.width() >= 20 && control.height() == 20);
                                        assertInside(layout.topBar(), control);
                                        assertFalse(overlap(header.title(), control));
                                        for (GuideUiLayout.Rect other : header.controls()) {
                                            if (control != other) assertFalse(overlap(control, other));
                                        }
                                    }
                                    assertReadableTranscript(layout);
                                    GuideUiLayout.ComposerExtras extras = layout.composerExtras(images, active, pending);
                                    assertTrue(extras.input().height() >= 24);
                                    if (images) assertTrue(extras.images().height() >= 12);
                                    if (active || pending > 0) assertTrue(extras.footer().height() >= 12);
                                    if (active) assertInside(layout.composer(), layout.composerControls().stop());
                                    assertInside(new GuideUiLayout.Rect(0, 0, size[0], size[1]), layout.telemetry());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void secondaryRowRequiresEnoughNativeHeightForInputAttachmentsAndStop() {
        GuideUiLayout room = GuideUiLayout.calculate(240, 180, false,
                220, 60, 48, 54, true, false, false, 0);
        assertEquals(40, room.topBar().height());
        assertEquals(220, room.header().title().width());
        assertTrue(room.header().sessions().y() >= room.header().title().bottom() + 2);
        GuideUiLayout busy = GuideUiLayout.calculate(240, 180, false,
                220, 60, 48, 54, true, true, true, 4);
        assertEquals(24, busy.topBar().height());
        assertTrue(busy.header().title().width() >= 130);
        assertTrue(busy.header().title().width() < 220);
        assertEquals(20, busy.header().model().width());
        assertEquals(20, busy.header().sessions().width());
        assertReadableTranscript(room);
        assertReadableTranscript(busy);
        assertTrue(busy.composerExtras(true, true, 4).input().height() >= 24);
        assertInside(busy.composer(), busy.composerControls().stop());
    }

    @Test
    void progressAndStopExistOnlyForAnActiveRequest() {
        GuideUiLayout idle = GuideUiLayout.calculate(240, 180, false);
        GuideUiLayout active = GuideUiLayout.calculate(240, 180, false,
                120, 60, 48, 54, true, false, true, 0);
        GuideUiLayout queuedIdle = GuideUiLayout.calculate(240, 180, false,
                120, 60, 48, 54, true, false, false, 4);
        assertEquals(0, idle.progress().height());
        assertEquals(0, queuedIdle.progress().height());
        assertEquals(GuideUiLayout.Rect.EMPTY, idle.composerControls().stop());
        assertEquals(GuideUiLayout.Rect.EMPTY, queuedIdle.composerControls().stop());
        assertTrue(active.progress().height() >= 12);
        assertTrue(active.composerControls().stop().height() > 0);
        assertInside(active.composer(), active.composerControls().stop());
        assertTrue(idle.composerControls().send().height() > 0);
        assertTrue(active.composerControls().send().height() > 0);
        assertTrue(idle.transcript().height() > active.transcript().height());
    }

    @Test
    void composerErrorsHaveTheirOwnVisibleBudgetAtEveryScale() {
        for (int[] size : new int[][] {{240, 180}, {320, 240}, {427, 320}, {900, 500}}) {
            GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], true,
                    120, 60, 48, 54, true, true, true, 4);
            GuideUiLayout.Rect notice = layout.composerNotice();
            assertEquals(10, notice.height());
            assertEquals(layout.composer().x(), notice.x());
            assertEquals(layout.composer().width(), notice.width());
            assertEquals(layout.composer().y(), notice.bottom());
            assertInside(new GuideUiLayout.Rect(0, 0, size[0], size[1]), notice);
            for (GuideUiLayout.Rect region : List.of(layout.transcript(), layout.progress(),
                    layout.composer(), layout.telemetry(), layout.sessionRail(), layout.detail())) {
                assertFalse(overlap(notice, region));
            }
            assertReadableTranscript(layout);
        }
    }

    @Test
    void normalLayoutKeepsRailAndInlineDetailOutsideTranscript() {
        GuideUiLayout layout = GuideUiLayout.calculate(900, 500, true);
        assertFalse(layout.narrow());
        assertFalse(layout.detailOverlay());
        assertTrue(layout.sessionRail().width() > 0);
        assertTrue(layout.transcript().x() > layout.sessionRail().right());
        assertTrue(layout.detail().x() >= layout.transcript().right());
        assertEquals(0, layout.progress().height());
        assertTrue(layout.transcript().bottom() <= layout.progress().y());
        assertTrue(layout.progress().bottom() <= layout.composerNotice().y());
    }

    @Test
    void hidingTheWideRailReturnsItsWidthAndUsesTheCompactTelemetryBudget() {
        GuideUiLayout shown = GuideUiLayout.calculate(900, 500, true,
                120, 60, 48, 54, true, true, true, 4, true);
        GuideUiLayout hidden = GuideUiLayout.calculate(900, 500, true,
                120, 60, 48, 54, true, true, true, 4, false);
        assertFalse(hidden.narrow());
        assertEquals(GuideUiLayout.Rect.EMPTY, hidden.sessionRail());
        assertTrue(hidden.transcript().width() > shown.transcript().width());
        assertEquals(shown.transcript().y(), hidden.transcript().y());
        assertEquals(shown.transcript().height() - 16, hidden.transcript().height());
        assertEquals(shown.detail().width(), hidden.detail().width());
        assertTrue(shown.telemetryCard());
        assertFalse(hidden.telemetryCard());
        assertEquals(hidden.transcript().width(), hidden.composer().width());
        assertReadableTranscript(hidden);
    }

    @Test
    void narrowLayoutUsesOverlayWithoutShrinkingTranscriptToNothing() {
        GuideUiLayout layout = GuideUiLayout.calculate(320, 240, true);
        assertTrue(layout.narrow());
        assertTrue(layout.detailOverlay());
        assertEquals(GuideUiLayout.Rect.EMPTY, layout.sessionRail());
        assertTrue(layout.transcript().width() > 250);
        assertTrue(layout.detail().width() <= 320 - 16);
        assertEquals(layout.composer().width(), layout.progress().width());
        assertReadableTranscript(layout);
    }

    @Test
    void unsupportedSizesAreRejectedRatherThanProduceAnUnreadableLayout() {
        assertThrows(IllegalArgumentException.class, () -> GuideUiLayout.calculate(239, 180, false));
        assertThrows(IllegalArgumentException.class, () -> GuideUiLayout.calculate(240, 179, false));
    }

    private static void assertReadableTranscript(GuideUiLayout layout) {
        assertTrue(layout.transcript().height() - 14 >= 4 * 10,
                "transcript must retain four actual text lines after viewport padding");
    }

    private static void assertInside(GuideUiLayout.Rect outer, GuideUiLayout.Rect inner) {
        assertTrue(inner.width() >= 0 && inner.height() >= 0);
        assertTrue(inner.x() >= outer.x());
        assertTrue(inner.y() >= outer.y());
        assertTrue(inner.right() <= outer.right());
        assertTrue(inner.bottom() <= outer.bottom());
    }

    private static boolean overlap(GuideUiLayout.Rect a, GuideUiLayout.Rect b) {
        if (a.width() == 0 || a.height() == 0 || b.width() == 0 || b.height() == 0) return false;
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }
}
