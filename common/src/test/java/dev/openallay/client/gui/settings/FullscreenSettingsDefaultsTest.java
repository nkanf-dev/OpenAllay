package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import org.junit.jupiter.api.Test;

final class FullscreenSettingsDefaultsTest {
    @Test
    void toolsStartExpandedAndExplicitFullscreenResetExpandsThemWithoutChangingOtherGroups() {
        assertFalse(GuideUiConfig.Fullscreen.defaults().toolsCollapsed());
        var defaults = GuideDisplayConfig.defaults();
        var ui = defaults.ui().withFullscreen(new GuideUiConfig.Fullscreen(
                GuideUiConfig.Density.COMPACT, false, true, GuideUiConfig.Theme.MINT))
                .withHud(defaults.ui().hud().withEnabled(true))
                .withNotifications(defaults.ui().notifications().withEnabled(true));
        var saved = defaults.withUi(ui).withAssistantName("Player name").withDebugMode(true);
        var draft = new UiSettingsDraft(saved);
        assertTrue(draft.ui().fullscreen().toolsCollapsed(), "Existing saved preference remains unchanged");
        draft.reset(UiSettingsProjection.Group.FULLSCREEN);
        assertFalse(draft.ui().fullscreen().toolsCollapsed());
        var candidate = draft.candidate(saved);
        assertEquals(ui.hud(), candidate.ui().hud());
        assertEquals(ui.notifications(), candidate.ui().notifications());
        assertEquals(saved.assistantName(), candidate.assistantName());
        assertTrue(candidate.debugMode());
    }
}
