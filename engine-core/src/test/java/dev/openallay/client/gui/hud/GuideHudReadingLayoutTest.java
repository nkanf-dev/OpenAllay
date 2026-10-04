package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.guide.ui.GuideUiLayout;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuideHudReadingLayoutTest {
    @Test void normalCompactPanelsReserveDistinctResultNoticeNavigationAndComposerStrips() {
        for (int width : new int[] {228, 320, 440}) for (int height : new int[] {180, 240, 400}) {
            var layout = GuideHudReadingLayout.calculate(6, 10, width, height);
            assertTrue(layout.footerFits());
            assertEquals(46, layout.results().y());
            assertEquals(height - 144, layout.results().height());
            assertEquals(10 + height - 108, layout.results().bottom());
            assertEquals(10 + height - 104, layout.notice().y());
            assertEquals(10, layout.notice().height());
            assertEquals(14, layout.navigation().height());
            assertEquals(38, layout.composer().height());
            assertEquals(20, layout.actions().height());
            var strips = List.of(layout.results(), layout.notice(), layout.navigation(), layout.composer(), layout.actions());
            for (int i = 0; i < strips.size() - 1; i++) {
                assertTrue(strips.get(i).bottom() <= strips.get(i + 1).y(), "native paint strips cannot overlap");
            }
            assertEquals(layout.results().y(), layout.scrollbar().y());
            assertEquals(layout.results().height(), layout.scrollbar().height());
            assertEquals(10 + height - 10, layout.actions().bottom());
        }
    }
    @Test void impossibleTinyPanelsExposeNoReplyPixelsAndNeverInventAFittingFooter() {
        for (int width : new int[] {0, 1, 20, 240}) for (int height : new int[] {0, 1, 36, 80, 120, 143}) {
            var layout = GuideHudReadingLayout.calculate(4, 8, width, height);
            assertFalse(layout.footerFits());
            assertEquals(0, layout.results().height());
            for (var rect : List.of(layout.results(), layout.notice(), layout.navigation(), layout.composer(), layout.actions(), layout.scrollbar())) {
                assertTrue(rect.height() >= 0 && rect.width() >= 0);
                assertTrue(rect.y() >= 8 && rect.bottom() <= 8 + height);
                assertTrue(rect.x() >= 4 && rect.right() <= 4 + width);
            }
        }
    }
    @Test void physicalBudgetNeverMutatesFullSourceReplyOrPassivePreviewConfiguration() {
        var layout = GuideHudReadingLayout.calculate(0, 0, 440, 180);
        var state = new dev.openallay.guide.ui.hud.GuideHudScrollState();
        var source = List.of(new dev.openallay.guide.ui.GuideTranscriptVirtualizer.Row("full-reply", 1400));
        state.update(source, layout.results().height());
        state.latest();
        assertEquals(1400 - 36, state.maximum());
        assertEquals(state.maximum(), state.offset());
        assertEquals(1400, source.getFirst().height());
    }
}
