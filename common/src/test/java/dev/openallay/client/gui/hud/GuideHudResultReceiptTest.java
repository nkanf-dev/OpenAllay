package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.guide.ui.GuideUiLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuideHudResultReceiptTest {
    @Test void paintedNodeEvidenceIsImmutableAndConvenienceReceiptDoesNotInventATail() {
        var ids = new ArrayList<>(List.of("last-painted-node"));
        var receipt = new GuideHudResultRenderer.Receipt(8, List.of("assistant:real-request:0"),
                1, 0, 0, 1, 0, 0, 1200, 200, 1000, 1000, 1, 0, ids, "ACTUAL_TAIL_MARKER");
        ids.clear();
        assertEquals(List.of("last-painted-node"), receipt.renderedNodeIds());
        assertEquals("ACTUAL_TAIL_MARKER", receipt.lastRenderedText());
        assertThrows(UnsupportedOperationException.class, () -> receipt.renderedNodeIds().clear());
        var beforePaint = new GuideHudResultRenderer.Receipt(0, List.of(),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(List.of(), beforePaint.renderedNodeIds());
        assertEquals("", beforePaint.lastRenderedText());
    }
    @Test void wrappedLinesReportOneStableSemanticNodeWithoutChangingSourceLineIds() {
        String semanticId = "a".repeat(64);
        assertEquals(semanticId, GuideHudResultRenderer.paintedNodeId(semanticId + "-line-0"));
        assertEquals(semanticId, GuideHudResultRenderer.paintedNodeId(semanticId + "-line-14"));
        assertEquals(semanticId, GuideHudResultRenderer.paintedNodeId(semanticId));
        assertEquals("custom-line-not-a-number", GuideHudResultRenderer.paintedNodeId("custom-line-not-a-number"));
    }
    @Test void onlyRowsThatIntersectTheNativeViewportCanBecomePaintEvidence() {
        var bounds = new GuideUiLayout.Rect(8, 36, 300, 150);
        assertFalse(GuideHudResultRenderer.visibleLine(bounds, 26, 10));
        assertFalse(GuideHudResultRenderer.visibleLine(bounds, 186, 10));
        assertTrue(GuideHudResultRenderer.visibleLine(bounds, 27, 10));
        assertTrue(GuideHudResultRenderer.visibleLine(bounds, 185, 10));
        assertFalse(GuideHudResultRenderer.visibleLine(bounds, 40, 0));
        assertFalse(GuideHudResultRenderer.visibleLine(new GuideUiLayout.Rect(8, 36, 300, 0), 36, 10));
    }
}
