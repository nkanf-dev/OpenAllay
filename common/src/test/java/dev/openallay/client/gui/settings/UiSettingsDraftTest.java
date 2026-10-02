package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

final class UiSettingsDraftTest {
    @Test
    void previewScaleAndAlphaAreMemoryOnlyAndCancelRestoresLastValid() {
        var saved = GuideDisplayConfig.defaults();
        var draft = new UiSettingsDraft(saved);
        draft.preview(saved.ui().withHud(saved.ui().hud().withBackgroundOpacity(.1)
                .withPlacement(GuideUiConfig.Anchor.BOTTOM_RIGHT, -24, -16, 320, 180, 1.5)));
        assertTrue(draft.dirty());
        assertEquals(1.5, draft.ui().hud().scale());
        assertEquals(.1, draft.ui().hud().backgroundOpacity());
        assertEquals(255, draft.ui().hud().textArgb(0) >>> 24);
        assertEquals(GuideDisplayConfig.defaults(), saved);
        draft.cancel();
        assertFalse(draft.dirty());
        assertEquals(saved.ui(), draft.ui());
    }

    @Test
    void groupResetAndApplyPreserveIndependentFieldsAndLatestGeneralChanges() {
        var ui = GuideUiConfig.defaults().withHud(GuideUiConfig.Hud.defaults().withEnabled(true))
                .withNotifications(GuideUiConfig.Notifications.defaults().withEnabled(true));
        var saved = new GuideDisplayConfig(false, false, "小羽", ui);
        var draft = new UiSettingsDraft(saved);
        draft.reset(UiSettingsProjection.Group.HUD);
        assertFalse(draft.ui().hud().enabled());
        assertTrue(draft.ui().notifications().enabled());
        assertFalse(draft.animationsEnabled());
        var latest = saved.withAssistantName("新名字").withDebugMode(true);
        var candidate = draft.candidate(latest);
        assertEquals("新名字", candidate.assistantName());
        assertTrue(candidate.debugMode());
        assertEquals(draft.ui(), candidate.ui());
        draft.published(candidate);
        assertFalse(draft.dirty());
        draft.reset(UiSettingsProjection.Group.FULLSCREEN);
        assertTrue(draft.animationsEnabled());
        assertTrue(draft.ui().notifications().enabled());
    }

    @Test
    void rejectedOrOlderSavePublicationCannotDiscardNewerSliderPreview() {
        var saved = GuideDisplayConfig.defaults();
        var draft = new UiSettingsDraft(saved);
        draft.preview(saved.ui().withHud(saved.ui().hud().withBackgroundOpacity(.5)));
        var inFlight = draft.candidate(saved);
        draft.preview(draft.ui().withHud(draft.ui().hud().withBackgroundOpacity(.2)));
        draft.published(inFlight);
        assertTrue(draft.dirty());
        assertEquals(.2, draft.ui().hud().backgroundOpacity());
        draft.published(saved); // failed save retained last valid publication
        assertEquals(.2, draft.ui().hud().backgroundOpacity());
        assertTrue(draft.dirty());
        draft.cancel();
        assertEquals(saved.ui(), draft.ui());
    }

    @Test
    void uiPageAlwaysProjectsIndependentControlsEvenWhenBothChannelsAreOff() {
        var projection = UiSettingsProjection.from(GuideDisplayConfig.defaults());
        assertEquals(List.of(UiSettingsProjection.Group.FULLSCREEN, UiSettingsProjection.Group.HUD,
                UiSettingsProjection.Group.NOTIFICATIONS), projection.groups());
        assertFalse(projection.config().hud().enabled());
        assertFalse(projection.config().notifications().enabled());
        assertTrue(projection.animationsEnabled());
        assertEquals("screen.openallay.settings.ui.hud", UiSettingsProjection.Group.HUD.translationKey());
    }
}
