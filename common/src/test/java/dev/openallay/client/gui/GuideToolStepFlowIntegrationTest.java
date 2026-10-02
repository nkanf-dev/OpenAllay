package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source boundary checks supplement pure layout tests; they are not graphical acceptance. */
final class GuideToolStepFlowIntegrationTest {
    @Test void toolRowsUseNativeStepBodyAndExactSharedMeasurementRatherThanGroupSummary() throws Exception {
        String source = screen();
        String renderRow = between(source, "    private int renderRow(", "    static Component factualRowText(");
        assertTrue(renderRow.contains("return renderToolStepCard(graphics, tool, x, y, width, mouseX, mouseY)"));
        assertFalse(renderRow.contains("screen.openallay.tools.summary"));
        assertFalse(renderRow.contains("toolSummaryLines("));
        String measured = between(source, "    private int measureRow(", "    private ToolStepBody toolStepBody(");
        assertTrue(measured.contains("toolStepGeometry(tool, 0, 0, width).rowHeight()"));
        String body = between(source, "    private int renderToolStepBody(", "    private void toolStepHit(");
        assertTrue(body.contains("semanticRenderer.render("));
        assertTrue(body.contains("NativeDomainViewBinding.Recipe"));
        assertTrue(body.contains("boolean painted = nativeViews.render("));
        assertTrue(body.contains("return painted;"));
        assertTrue(body.contains("intersects(bounds, layout.transcript())"));
        assertFalse(body.contains("normalized()"));
        assertFalse(body.contains("DEBUG_GSON"));
        assertFalse(body.contains("service.submit("));
    }

    @Test void nativeActionsAndFoldControlsOwnHitRegionsBeforeInertCardBackground() throws Exception {
        String source = screen();
        String card = between(source, "    private int renderToolStepCard(", "    private int renderToolStepBody(");
        assertTrue(card.indexOf("renderToolStepControl(") < card.indexOf("renderToolStepBody("));
        assertTrue(card.indexOf("renderToolStepBody(") < card.indexOf("toolStepHit(card, () -> {}"));
        assertTrue(card.contains("rowId + \":collapse\""));
        assertTrue(card.contains("rowId + \":task-collapse\""));
        assertTrue(card.contains("rowId + \":detail\""));
        String hit = between(source, "    private void toolStepHit(", "    private void renderToolStepControl(");
        assertTrue(hit.contains("Math.max(bounds.x(), viewport.x())"));
        assertTrue(hit.contains("Math.min(bounds.bottom(), viewport.bottom())"));
        assertTrue(hit.contains("HitKind.CONTENT, action, id, narration"));
        String transcript = between(source, "    private void renderTranscript(", "    private int renderRow(");
        assertTrue(transcript.contains("nativeViews.beginFrame()"));
        assertTrue(transcript.contains("nativeViews.endFrame()"));
        assertTrue(transcript.contains("collapsed step still has its own actual header"));
    }

    @Test void typedCacheAndEphemeralFoldsHaveExactOwnerAndPresentationBoundaries() throws Exception {
        String source = screen();
        String update = between(source, "    private void updateVirtualRows(", "    private int measureRow(");
        assertTrue(update.contains("ToolFlowOwner nextOwner = toolFlowOwner()"));
        assertTrue(source.contains("new ToolFlowOwner(service.snapshot().actorId(), view.selectedSession()"));
        assertTrue(source.contains("minecraft.level"));
        assertTrue(update.contains("projectedDisplay.ui().fullscreen().toolsCollapsed()"));
        assertTrue(update.contains("toolStepBodies.clear()"));
        assertTrue(update.contains("nativeViews.clear()"));
        assertTrue(update.contains("toolStepBodies.keySet().removeIf"));
        String bodyCache = between(source, "    private ToolStepBody toolStepBody(", "    private dev.openallay.guide.ui.GuideToolStepFlowGeometry toolStepGeometry(");
        assertTrue(bodyCache.contains("GuideHudToolCards.project("));
        assertTrue(bodyCache.contains("Language.getInstance()"));
        assertTrue(bodyCache.contains("System.identityHashCode(font)"));
        assertTrue(bodyCache.contains("semanticLayouts.invalidateRow(id)"));
        assertTrue(bodyCache.contains("toolFailureComponents(tool.detail()"));
        String menu = between(source, "    private void renderOverflow(", "    private void microphoneAction(");
        assertFalse(menu.contains("screen.openallay.tools.collapse"));
        assertFalse(menu.contains("expandedToolRequests"));
    }

    private static String screen() throws Exception {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path root = current.getFileName() != null && current.getFileName().toString().equals("common")
                ? current.getParent() : current;
        return Files.readString(root.resolve("common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java"));
    }
    private static String between(String source, String from, String to) {
        int start = source.indexOf(from);
        assertTrue(start >= 0, from);
        int end = source.indexOf(to, start + from.length());
        assertTrue(end > start, to);
        return source.substring(start, end);
    }
}
