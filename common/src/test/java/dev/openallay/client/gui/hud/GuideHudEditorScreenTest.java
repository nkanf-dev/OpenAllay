package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuideHudEditorScreenTest {
    @Test
    void draftChangesStayInMemoryUntilApplyAndPreserveOtherDisplaySettings() {
        GuideDisplayConfig original = GuideDisplayConfig.defaults();
        List<GuideDisplayConfig> received = new ArrayList<>();
        GuideHudEditorScreen.Draft draft = new GuideHudEditorScreen.Draft(original, received::add);
        GuideUiConfig.Hud originalHud = original.ui().hud();
        GuideUiConfig.Hud changed = originalHud.withBackgroundOpacity(0.25).withEnabled(false);
        draft.update(changed);
        assertTrue(received.isEmpty());
        assertSame(originalHud, original.ui().hud());
        assertEquals(changed, draft.candidate().ui().hud());
        assertEquals(original.assistantName(), draft.candidate().assistantName());
        assertEquals(original.debugMode(), draft.candidate().debugMode());
        assertEquals(original.animationsEnabled(), draft.candidate().animationsEnabled());
        assertEquals(original.ui(), draft.candidate().ui().withHud(original.ui().hud()));
        GuideDisplayConfig candidate = draft.candidate();
        assertTrue(draft.apply(true));
        assertEquals(List.of(candidate), received);
        assertSame(candidate, received.getFirst());
        assertFalse(draft.apply(true));
        draft.update(original.ui().hud());
        draft.cancel();
        assertSame(candidate, draft.candidate());
        assertEquals(1, received.size());
    }

    @Test
    void escapeCancelDiscardsAllUnsavedEditsAndNeverInvokesApply() {
        GuideDisplayConfig original = GuideDisplayConfig.defaults();
        List<GuideDisplayConfig> received = new ArrayList<>();
        GuideHudEditorScreen.Draft draft = new GuideHudEditorScreen.Draft(original, received::add);
        draft.update(original.ui().hud().withBackgroundOpacity(0));
        draft.cancel();
        draft.cancel();
        assertTrue(draft.finished());
        assertSame(original, draft.candidate());
        assertFalse(draft.apply(true));
        draft.update(original.ui().hud().withEnabled(false));
        assertSame(original, draft.candidate());
        assertTrue(received.isEmpty());
    }

    @Test
    void disconnectedOwnerCannotApplyOrRetainItsEditedCandidate() {
        GuideDisplayConfig original = GuideDisplayConfig.defaults();
        List<GuideDisplayConfig> received = new ArrayList<>();
        GuideHudEditorScreen.Draft draft = new GuideHudEditorScreen.Draft(original, received::add);
        draft.update(original.ui().hud().withEnabled(false));
        assertFalse(draft.apply(false));
        assertTrue(draft.finished());
        assertSame(original, draft.candidate());
        assertFalse(draft.apply(true));
        assertTrue(received.isEmpty());
    }

    @Test
    void opacityChangesOnlyBackgroundOpacityAndDoesNotChangePlacementOrTextScale() {
        GuideDisplayConfig original = GuideDisplayConfig.defaults();
        GuideHudEditorScreen.Draft draft = new GuideHudEditorScreen.Draft(original, ignored -> {});
        GuideUiConfig.Hud before = draft.hud();
        draft.update(before.withBackgroundOpacity(0));
        GuideUiConfig.Hud after = draft.hud();
        assertEquals(0, after.backgroundOpacity());
        assertEquals(before.anchor(), after.anchor());
        assertEquals(before.offsetX(), after.offsetX());
        assertEquals(before.offsetY(), after.offsetY());
        assertEquals(before.width(), after.width());
        assertEquals(before.height(), after.height());
        assertEquals(before.scale(), after.scale());
        assertEquals(before.collapsed(), after.collapsed());
        assertEquals(before.showLatestReply(), after.showLatestReply());
        assertEquals(before.showStreamingPreview(), after.showStreamingPreview());
    }

    @Test
    void dragUpdatesOnlyTheDraftAndCancelledGestureCannotContinueAfterFocusLossOrResize() {
        List<GuideDisplayConfig> received = new ArrayList<>();
        GuideHudEditorScreen.Draft draft = positionedDraft(received);
        GuideHudEditorScreen.Interaction interaction = new GuideHudEditorScreen.Interaction(draft);
        GuideHudLayout.Rect before = GuideHudLayout.calculate(960, 540, draft.hud());
        assertTrue(interaction.begin(960, 540, before.x() + 10, before.y() + 10));
        assertTrue(interaction.active());
        interaction.move(960, 540, before.x() + 30, before.y() + 25);
        GuideHudLayout.Rect after = GuideHudLayout.calculate(960, 540, draft.hud());
        assertEquals(before.x() + 20, after.x());
        assertEquals(before.y() + 15, after.y());
        assertEquals(before.width(), after.width());
        assertEquals(before.scale(), after.scale());
        GuideDisplayConfig candidate = draft.candidate();
        interaction.cancel();
        interaction.move(960, 540, 700, 400);
        assertFalse(interaction.active());
        assertSame(candidate, draft.candidate());
        assertTrue(received.isEmpty());
    }

    @Test
    void resizeGestureChangesContentSizeAndNotTheScaleOrTopLeft() {
        List<GuideDisplayConfig> received = new ArrayList<>();
        GuideHudEditorScreen.Draft draft = positionedDraft(received);
        GuideHudEditorScreen.Interaction interaction = new GuideHudEditorScreen.Interaction(draft);
        GuideHudLayout.Rect before = GuideHudLayout.calculate(960, 540, draft.hud());
        double x = before.right() - 2;
        double y = before.bottom() - 2;
        assertTrue(GuideHudEditorScreen.Interaction.onHandle(before, x, y));
        assertTrue(interaction.begin(960, 540, x, y));
        interaction.move(960, 540, x + 50, y + 25);
        GuideHudLayout.Rect after = GuideHudLayout.calculate(960, 540, draft.hud());
        assertEquals(before.x(), after.x());
        assertEquals(before.y(), after.y());
        assertEquals(before.width() + 50, after.width());
        assertEquals(before.height() + 25, after.height());
        assertEquals(before.scale(), after.scale());
        assertTrue(received.isEmpty());
    }

    @Test
    void outsideClicksAndFinishedDraftsCannotStartEditing() {
        GuideHudEditorScreen.Draft draft = positionedDraft(new ArrayList<>());
        GuideHudEditorScreen.Interaction interaction = new GuideHudEditorScreen.Interaction(draft);
        assertFalse(interaction.begin(960, 540, 0, 0));
        assertFalse(interaction.active());
        GuideHudLayout.Rect bounds = GuideHudLayout.calculate(960, 540, draft.hud());
        draft.cancel();
        assertFalse(interaction.begin(960, 540, bounds.x() + 10, bounds.y() + 10));
        assertFalse(interaction.active());
    }

    @Test
    void cancelledDraftCannotReceiveMorePointerChanges() {
        GuideHudEditorScreen.Draft draft = positionedDraft(new ArrayList<>());
        GuideHudEditorScreen.Interaction interaction = new GuideHudEditorScreen.Interaction(draft);
        GuideHudLayout.Rect bounds = GuideHudLayout.calculate(960, 540, draft.hud());
        assertTrue(interaction.begin(960, 540, bounds.x() + 10, bounds.y() + 10));
        draft.cancel();
        GuideDisplayConfig cancelled = draft.candidate();
        interaction.move(960, 540, bounds.x() + 50, bounds.y() + 50);
        assertSame(cancelled, draft.candidate());
    }

    @Test
    void short180PixelViewportKeepsEveryControlAndFixedApplyCancelFooterVisible() {
        int viewportHeight = 180;
        int panelHeight = Math.min(190, viewportHeight - 12);
        int panelY = Math.max(6, (viewportHeight - panelHeight) / 2);
        GuideHudEditorScreen.Form form = GuideHudEditorScreen.Form.calculate(panelY, panelHeight);
        assertEquals(16, form.rowHeight());
        assertEquals(0, form.maximumScroll());
        for (int row = 0; row < 6; row++) {
            assertTrue(form.rowVisible(row, 0), "row " + row);
            assertTrue(form.rowY(row, 0) >= panelY);
            assertTrue(form.rowY(row, 0) + form.rowHeight() <= form.footerY());
        }
        assertTrue(form.footerY() >= panelY);
        assertTrue(form.footerY() + form.footerHeight() <= panelY + panelHeight);
        assertTrue(form.footerY() + form.footerHeight() <= viewportHeight - 6);
    }

    @Test
    void evenShorterFormScrollsEveryNativeRowWithoutMovingItsFooter() {
        int viewportHeight = 120;
        int panelHeight = viewportHeight - 12;
        int panelY = 6;
        GuideHudEditorScreen.Form form = GuideHudEditorScreen.Form.calculate(panelY, panelHeight);
        assertTrue(form.maximumScroll() > 0);
        for (int row = 0; row < 6; row++) {
            boolean reachable = false;
            for (int scroll = 0; scroll <= form.maximumScroll(); scroll += 20) {
                reachable |= form.rowVisible(row, scroll);
            }
            reachable |= form.rowVisible(row, form.maximumScroll());
            assertTrue(reachable, "row " + row);
        }
        assertTrue(form.bodyBottom() < form.footerY());
        assertTrue(form.footerY() + form.footerHeight() <= viewportHeight - 6);
    }

    private static GuideHudEditorScreen.Draft positionedDraft(List<GuideDisplayConfig> received) {
        GuideDisplayConfig original = GuideDisplayConfig.defaults();
        GuideUiConfig.Hud hud = original.ui().hud().withPlacement(
                GuideUiConfig.Anchor.values()[0], 100, 100, 240, 100, 1.25);
        original = original.withUi(original.ui().withHud(hud));
        return new GuideHudEditorScreen.Draft(original, received::add);
    }
}
