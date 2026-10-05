package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.guide.ui.GuideUiLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Pure signature/source guards. Actual native resize and pre-paint clicks remain E2E checks. */
final class GuideHudHitCacheContractsTest {
    private static String source(String name) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("repository root unavailable");
        return Files.readString(root.resolve("common/src/main/java/dev/openallay/client/gui/hud/" + name + ".java"));
    }

    @Test void paintedSignatureAcceptsOnlyItsCurrentResourceGeometryAndOffset() {
        Object font = new Object(), language = new Object();
        var viewport = new GuideUiLayout.Rect(8, 36, 300, 150);
        var painted = new GuideHudResultRenderer.PaintedHits(7, font, language, viewport, 240);
        assertTrue(painted.current(7, font, language, new GuideUiLayout.Rect(8, 36, 300, 150), 240));
        assertFalse(painted.current(8, font, language, viewport, 240));
        assertFalse(painted.current(7, new Object(), language, viewport, 240));
        assertFalse(painted.current(7, font, new Object(), viewport, 240));
        assertFalse(painted.current(7, font, language, new GuideUiLayout.Rect(9, 36, 300, 150), 240));
        assertFalse(painted.current(7, font, language, new GuideUiLayout.Rect(8, 37, 300, 150), 240));
        assertFalse(painted.current(7, font, language, new GuideUiLayout.Rect(8, 36, 301, 150), 240));
        assertFalse(painted.current(7, font, language, new GuideUiLayout.Rect(8, 36, 300, 151), 240));
        assertFalse(painted.current(7, font, language, viewport, 241));
        assertFalse(painted.current(7, font, language, null, 240));
    }

    @Test void invalidationDropsActionsWithoutInventingPaintEvidence() throws Exception {
        String renderer = source("GuideHudResultRenderer");
        int invalidate = renderer.indexOf("public void invalidateHits()");
        String invalidation = renderer.substring(invalidate, renderer.indexOf("public void invalidate()", invalidate));
        assertTrue(invalidation.contains("hitEpoch++"));
        assertTrue(invalidation.contains("paintedHits = null"));
        assertTrue(invalidation.contains("hits.clear()"));
        assertFalse(invalidation.contains("receipt ="));
        assertFalse(invalidation.contains("extractedFrame"));
        int getter = renderer.indexOf("public List<Hit> hits()");
        String getters = renderer.substring(getter, renderer.indexOf("public boolean detailOpen()", getter));
        assertTrue(getters.contains("paintedHits.current(hitEpoch, font, Language.getInstance(), viewport, scroll.offset())"));
        assertFalse(getters.contains("paintedHits = null"));
        assertFalse(getters.contains("paintedHits = new"));
        assertFalse(getters.contains("receipt ="));
        assertFalse(getters.contains("extractedFrame"));
        assertFalse(getters.contains("prepare("));
        assertFalse(getters.contains("render("));
        assertTrue(renderer.contains("if (changed || viewportChanged) invalidateHits()"));
        assertTrue(renderer.contains("!view.selectedSession().equals(cachedSession)"));
        assertTrue(renderer.contains("releaseNativeViews() { invalidateHits();"));
        assertTrue(renderer.contains("close() { invalidateHits();"));
        assertTrue(renderer.contains("if (interactive) paintedHits = new PaintedHits("));
        assertEquals(1, renderer.split("\\+\\+extractedFrame", -1).length - 1);
    }

    @Test void compactNavigationInvalidatesOnlyWhenItsPaintedTransformMoves() throws Exception {
        String lite = source("GuideChatLiteScreen");
        assertTrue(lite.contains("results.hits(font, resultBounds)"));
        assertTrue(lite.contains("if (previousOffset != results.scroll().offset())"));
        assertTrue(lite.contains("results.invalidateHits()"));
        for (String action : new String[]{"wheel(scrollY)", "page(-1)", "page(1)", "first()", "latest()", "move((y -"}) {
            assertTrue(lite.contains("scrollResults(() -> results.scroll()." + action), action);
        }
        assertTrue(lite.contains("if (results.prepare(view, font, Math.max(1, resultBounds.width() - 6), resultBounds.height())) focusedResult = -1"));
    }

    @Test void compactResizeReusesTheNativeEditorAndRestoresOnlyPriorComposerFocus() throws Exception {
        String lite = source("GuideChatLiteScreen");
        int initStart = lite.indexOf("protected void init()");
        int reposition = lite.indexOf("protected void repositionElements()", initStart);
        String init = lite.substring(initStart, reposition);
        assertTrue(init.contains("if (composer == null)"));
        assertTrue(init.contains("GuideComposerGeometry.resize(composer, input)"));
        assertTrue(init.contains("if (!composer.getValue().equals(state.readText(session))) dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer, state.readText(session), true)"));
        assertTrue(init.contains("addRenderableWidget(composer.widget())"));
        assertTrue(init.contains("composer.widget().visible = readingLayout.footerFits()"));
        assertTrue(init.contains("setFocused(null)"));
        String rebuild = lite.substring(reposition, lite.indexOf("public void added()", reposition));
        assertTrue(rebuild.indexOf("getFocused() == composer.widget()") < rebuild.indexOf("rebuildWidgets()"));
        assertTrue(rebuild.contains("if (composerFocused && composer.widget().visible && composer.widget().active) setFocused(composer.widget())"));
        assertFalse(rebuild.contains("setFocused(send)"));
        assertTrue(lite.contains("guideInitialFocus() {}"));
    }
}
