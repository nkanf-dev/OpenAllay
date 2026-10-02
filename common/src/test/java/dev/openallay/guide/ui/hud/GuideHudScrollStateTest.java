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
    @Test void passivePagesKeepFullContentAccessibleWithoutPointerCapture() {
        assertEquals(14, GuideHudScrollState.passivePageCount(2020, 150));
        assertEquals(0, GuideHudScrollState.passivePageOffset(2020, 150, 0));
        assertEquals(150, GuideHudScrollState.passivePageOffset(2020, 150, 160));
        assertEquals(1870, GuideHudScrollState.passivePageOffset(2020, 150, 160 * 13));
        assertEquals(0, GuideHudScrollState.passivePageOffset(2020, 150, 160 * 14));
        assertEquals(1, GuideHudScrollState.passivePageCount(0, 0));
    }
}
