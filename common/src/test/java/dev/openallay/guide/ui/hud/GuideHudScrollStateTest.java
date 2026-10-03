package dev.openallay.guide.ui.hud;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.guide.ui.GuideTranscriptVirtualizer;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuideHudScrollStateTest {
    @Test void fractionalWheelAndKeyboardReachAllContentBeyondTenLines() {
        var state = new GuideHudScrollState();
        var rows = List.of(new GuideTranscriptVirtualizer.Row("long-reply", 1800),
                new GuideTranscriptVirtualizer.Row("tool-result", 220));
        state.update(rows, 140);
        assertEquals(1880, state.maximum());
        assertEquals(state.maximum(), state.offset(), "a new compact viewport starts at the actual tail");
        assertTrue(state.followingLatest());
        state.first();
        for (int i = 0; i < 10; i++) state.wheel(-.125);
        assertEquals(30, state.offset(), "smooth wheel keeps fractional increments");
        state.page(1);
        assertEquals(158, state.offset());
        state.latest();
        assertEquals(state.maximum(), state.offset());
        assertTrue(state.followingLatest());
        assertEquals(1, state.visible(0).fromIndex());
        state.first();
        assertEquals(0, state.offset());
        assertFalse(state.followingLatest());
        assertFalse(state.wheel(Double.NaN));
        assertEquals(1800, rows.getFirst().height(), "navigation has no content mutation path");
    }
    @Test void changedHeightsAndResizePreserveTheVisibleSemanticRowWhenNotFollowingLatest() {
        var state = new GuideHudScrollState();
        state.update(List.of(new GuideTranscriptVirtualizer.Row("assistant:1", 600),
                new GuideTranscriptVirtualizer.Row("tool:1:stable-call", 480),
                new GuideTranscriptVirtualizer.Row("assistant:2", 1000)), 160);
        state.move(672);
        assertEquals("tool:1:stable-call", state.anchor().rowId());
        assertEquals(72, state.anchor().pixelOffset());
        state.update(List.of(new GuideTranscriptVirtualizer.Row("assistant:1", 720),
                new GuideTranscriptVirtualizer.Row("tool:1:stable-call", 620),
                new GuideTranscriptVirtualizer.Row("assistant:2", 1200)), 220);
        assertEquals(792, state.offset());
        assertEquals("tool:1:stable-call", state.anchor().rowId());
        assertEquals(72, state.anchor().pixelOffset());
        state.latest();
        state.update(List.of(new GuideTranscriptVirtualizer.Row("assistant:1", 800),
                new GuideTranscriptVirtualizer.Row("tool:1:stable-call", 640),
                new GuideTranscriptVirtualizer.Row("assistant:2", 1500)), 260);
        assertEquals(state.maximum(), state.offset());
        assertTrue(state.followingLatest());
    }
    @Test void defaultFollowingUsesMeasuredTailForNewContentAndViewportChanges() {
        var state = new GuideHudScrollState();
        state.update(List.of(new GuideTranscriptVirtualizer.Row("reply", 2020)), 150);
        assertEquals(1870, state.offset());
        for (int frame = 0; frame < 100; frame++) {
            state.update(List.of(new GuideTranscriptVirtualizer.Row("reply", 2020)), 150);
            assertEquals(1870, state.offset(), "elapsed frames never rotate back to the first page");
        }
        state.update(List.of(new GuideTranscriptVirtualizer.Row("reply", 2600)), 180);
        assertEquals(2420, state.offset());
        state.update(List.of(new GuideTranscriptVirtualizer.Row("reply", 2600)), 90);
        assertEquals(2510, state.offset());
        assertTrue(state.followingLatest());
    }
    @Test void manualWheelStopsFollowingEvenAtTheBoundaryUntilExplicitLatest() {
        var state = new GuideHudScrollState();
        state.update(List.of(new GuideTranscriptVirtualizer.Row("reply", 600)), 150);
        assertFalse(state.wheel(0));
        assertFalse(state.wheel(Double.POSITIVE_INFINITY));
        assertTrue(state.followingLatest(), "invalid input does not change reading ownership");
        assertTrue(state.wheel(-1));
        assertEquals(450, state.offset(), "wheel at the tail is clamped");
        assertFalse(state.followingLatest(), "only Latest explicitly resumes wheel-following");
        state.update(List.of(new GuideTranscriptVirtualizer.Row("reply", 900)), 150);
        assertEquals(450, state.offset(), "new content cannot pull a manual reader to the tail");
        state.latest();
        assertEquals(750, state.offset());
        assertTrue(state.followingLatest());
        state.page(-1);
        assertEquals(612, state.offset());
        assertFalse(state.followingLatest());
        state.update(List.of(new GuideTranscriptVirtualizer.Row("reply", 1000)), 150);
        assertEquals(612, state.offset());
        state.latest();
        assertEquals(850, state.offset());
        state.move(state.maximum());
        assertFalse(state.followingLatest(), "manual scrollbar movement at the tail also keeps reading ownership");
        state.page(1);
        assertFalse(state.followingLatest(), "Page Down at the boundary is not an implicit Latest action");
        state.update(List.of(new GuideTranscriptVirtualizer.Row("reply", 1200)), 150);
        assertEquals(850, state.offset());
        state.latest();
        assertEquals(1050, state.offset());
        assertTrue(state.followingLatest());
    }
    @Test void tinyAndEmptyViewportsKeepSourceRowsWithoutInventingVisibleContent() {
        var state = new GuideHudScrollState();
        state.update(List.of(new GuideTranscriptVirtualizer.Row("actual-reply", 600)), 0);
        assertEquals(600, state.totalHeight());
        assertEquals(600, state.maximum());
        assertEquals(600, state.offset());
        state.update(List.of(), 0);
        assertEquals(0, state.offset());
        assertEquals(0, state.totalHeight());
        assertTrue(state.followingLatest());
    }
}
