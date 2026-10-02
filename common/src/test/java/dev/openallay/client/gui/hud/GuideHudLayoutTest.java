package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.ui.GuideUiConfig;
import org.junit.jupiter.api.Test;

final class GuideHudLayoutTest {
    private static final double EPSILON = 0.000001;

    @Test
    void allNineAnchorsUseScaledDimensionsAndGuiSpaceOffsets() {
        assertEquals(9, GuideUiConfig.Anchor.values().length);
        for (GuideUiConfig.Anchor anchor : GuideUiConfig.Anchor.values()) {
            GuideUiConfig.Hud hud = hud(anchor, 11, -7, 240, 100, 1.25);
            GuideHudLayout.Rect bounds = GuideHudLayout.calculate(960, 540, hud);
            assertEquals(240, bounds.contentWidth());
            assertEquals(100, bounds.contentHeight());
            assertEquals(300, bounds.width(), EPSILON);
            assertEquals(125, bounds.height(), EPSILON);
            assertEquals(1.25, bounds.scale(), EPSILON);
            assertEquals(Math.max(6, Math.min(654, anchor.xFactor() * 660 + 11)), bounds.x(), EPSILON);
            assertEquals(Math.max(6, Math.min(409, anchor.yFactor() * 415 - 7)), bounds.y(), EPSILON);
        }
    }

    @Test
    void everyAnchorAndExtremeOffsetRemainsInsideCurrentViewport() {
        for (GuideUiConfig.Anchor anchor : GuideUiConfig.Anchor.values()) {
            for (int offset : new int[] {-4096, 0, 4096}) {
                GuideUiConfig.Hud hud = hud(anchor, offset, -offset, 480, 240, 1.75);
                GuideHudLayout.Rect bounds = GuideHudLayout.calculate(320, 240, hud);
                assertTrue(bounds.x() >= 6);
                assertTrue(bounds.y() >= 6);
                assertTrue(bounds.right() <= 314 + EPSILON);
                assertTrue(bounds.bottom() <= 234 + EPSILON);
            }
        }
    }

    @Test
    void smallViewportsShrinkEffectiveContentWithoutRewritingTheSavedHud() {
        GuideUiConfig.Hud hud = hud(GuideUiConfig.Anchor.values()[8], 23, 17, 480, 240, 1.75);
        GuideHudLayout.Rect small = GuideHudLayout.calculate(320, 180, hud);
        assertEquals(176, small.contentWidth());
        assertEquals(96, small.contentHeight());
        assertEquals(308, small.width(), EPSILON);
        assertEquals(168, small.height(), EPSILON);
        assertEquals(480, hud.width());
        assertEquals(240, hud.height());
        assertEquals(23, hud.offsetX());
        assertEquals(17, hud.offsetY());
        GuideHudLayout.Rect restored = GuideHudLayout.calculate(1600, 900, hud);
        assertEquals(480, restored.contentWidth());
        assertEquals(240, restored.contentHeight());
    }

    @Test
    void evenTinyOrZeroViewportsHaveFiniteNonnegativeBounds() {
        GuideUiConfig.Hud hud = GuideUiConfig.Hud.defaults();
        for (int width : new int[] {0, 1, 6, 12, 13, 20}) {
            for (int height : new int[] {0, 1, 6, 12, 13, 20}) {
                GuideHudLayout.Rect bounds = GuideHudLayout.calculate(width, height, hud);
                assertTrue(Double.isFinite(bounds.x()));
                assertTrue(Double.isFinite(bounds.y()));
                assertTrue(bounds.x() >= 0);
                assertTrue(bounds.y() >= 0);
                assertTrue(bounds.width() >= 0);
                assertTrue(bounds.height() >= 0);
                assertTrue(bounds.right() <= width + EPSILON);
                assertTrue(bounds.bottom() <= height + EPSILON);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> GuideHudLayout.calculate(-1, 100, hud));
        assertThrows(IllegalArgumentException.class, () -> GuideHudLayout.calculate(100, -1, hud));
    }

    @Test
    void dragConvertsPositionIntoAnchorOffsetsWithoutChangingSizeOrScale() {
        for (GuideUiConfig.Anchor anchor : GuideUiConfig.Anchor.values()) {
            GuideUiConfig.Hud original = hud(anchor, 0, 0, 240, 100, 1.25);
            GuideUiConfig.Hud moved = GuideHudLayout.placementAt(960, 540, original, 300, 200);
            GuideHudLayout.Rect bounds = GuideHudLayout.calculate(960, 540, moved);
            assertEquals(300, bounds.x(), 0.5 + EPSILON);
            assertEquals(200, bounds.y(), 0.5 + EPSILON);
            assertEquals(anchor, moved.anchor());
            assertEquals(original.width(), moved.width());
            assertEquals(original.height(), moved.height());
            assertEquals(original.scale(), moved.scale());
            assertEquals(original.backgroundOpacity(), moved.backgroundOpacity());
        }
    }

    @Test
    void dragOutsideTheViewportStoresTheNearestVisiblePlacement() {
        GuideUiConfig.Hud hud = hud(GuideUiConfig.Anchor.values()[4], 0, 0, 240, 100, 1);
        GuideUiConfig.Hud moved = GuideHudLayout.placementAt(960, 540, hud, -1000, 1000);
        GuideHudLayout.Rect bounds = GuideHudLayout.calculate(960, 540, moved);
        assertEquals(6, bounds.x(), EPSILON);
        assertEquals(534, bounds.bottom(), EPSILON);
    }

    @Test
    void changingAnchorPreservesPhysicalPositionWithinIntegerOffsetPrecision() {
        GuideUiConfig.Hud original = hud(GuideUiConfig.Anchor.values()[0], 317, 181, 237, 99, 1.25);
        GuideHudLayout.Rect before = GuideHudLayout.calculate(960, 540, original);
        for (GuideUiConfig.Anchor anchor : GuideUiConfig.Anchor.values()) {
            GuideUiConfig.Hud changed = GuideHudLayout.withAnchorKeepingPosition(960, 540, original, anchor);
            GuideHudLayout.Rect after = GuideHudLayout.calculate(960, 540, changed);
            assertEquals(before.x(), after.x(), 0.5 + EPSILON);
            assertEquals(before.y(), after.y(), 0.5 + EPSILON);
            assertEquals(anchor, changed.anchor());
            assertEquals(original.width(), changed.width());
            assertEquals(original.height(), changed.height());
            assertEquals(original.scale(), changed.scale());
        }
    }

    @Test
    void resizeUsesUnscaledContentDimensionsAndRetainsTheTopLeft() {
        GuideUiConfig.Hud original = hud(GuideUiConfig.Anchor.values()[8], -100, -80, 240, 100, 1.25);
        GuideHudLayout.Rect before = GuideHudLayout.calculate(1200, 800, original);
        GuideUiConfig.Hud resized = GuideHudLayout.resizeAt(1200, 800, original,
                before.x(), before.y(), 350, 150);
        GuideHudLayout.Rect after = GuideHudLayout.calculate(1200, 800, resized);
        assertEquals(280, resized.width());
        assertEquals(120, resized.height());
        assertEquals(1.25, resized.scale());
        assertEquals(before.x(), after.x(), EPSILON);
        assertEquals(before.y(), after.y(), EPSILON);
        assertEquals(350, after.width(), EPSILON);
        assertEquals(150, after.height(), EPSILON);
    }

    @Test
    void resizeRespectsSavedDimensionRangesRatherThanChangingScale() {
        GuideUiConfig.Hud original = hud(GuideUiConfig.Anchor.values()[0], 6, 6, 240, 100, 1.5);
        GuideUiConfig.Hud smaller = GuideHudLayout.resizeAt(960, 540, original, 6, 6, -999, -999);
        GuideUiConfig.Hud larger = GuideHudLayout.resizeAt(960, 540, original, 6, 6, 9999, 9999);
        assertEquals(160, smaller.width());
        assertEquals(44, smaller.height());
        assertEquals(480, larger.width());
        assertEquals(240, larger.height());
        assertEquals(original.scale(), smaller.scale());
        assertEquals(original.scale(), larger.scale());
    }

    @Test
    void boundsIncludeTheTopLeftButNotTheRightOrBottomEdge() {
        GuideHudLayout.Rect bounds = GuideHudLayout.calculate(960, 540, GuideUiConfig.Hud.defaults());
        assertTrue(bounds.contains(bounds.x(), bounds.y()));
        assertFalse(bounds.contains(bounds.right(), bounds.y()));
        assertFalse(bounds.contains(bounds.x(), bounds.bottom()));
        assertFalse(bounds.contains(bounds.x() - 1, bounds.y()));
    }

    private static GuideUiConfig.Hud hud(
            GuideUiConfig.Anchor anchor, int x, int y, int width, int height, double scale) {
        return GuideUiConfig.Hud.defaults().withPlacement(anchor, x, y, width, height, scale);
    }
}
