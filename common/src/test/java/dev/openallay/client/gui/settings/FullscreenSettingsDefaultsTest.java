package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FullscreenSettingsDefaultsTest {
    @Test
    void defaultsAndProjectionExposeCurrentFullscreenPreferences() {
        var expected = new GuideUiConfig.Fullscreen(
                GuideUiConfig.Density.COMFORTABLE, true, GuideUiConfig.Theme.CHARCOAL);
        assertEquals(expected, GuideUiConfig.Fullscreen.defaults());
        var defaults = GuideDisplayConfig.defaults();
        assertEquals(expected, defaults.ui().fullscreen());
        var projection = UiSettingsProjection.from(defaults);
        assertEquals(expected, projection.config().fullscreen());
        assertTrue(projection.animationsEnabled());
    }

    @Test
    void fullscreenResetRestoresCurrentDefaultsAndAnimationsWithoutChangingOtherGroups() {
        var defaults = GuideDisplayConfig.defaults();
        var ui = defaults.ui().withFullscreen(new GuideUiConfig.Fullscreen(
                GuideUiConfig.Density.COMPACT, false, GuideUiConfig.Theme.MINT))
                .withHud(defaults.ui().hud().withEnabled(true))
                .withNotifications(defaults.ui().notifications().withEnabled(true));
        var saved = defaults.withUi(ui).withAnimationsEnabled(false);
        var draft = new UiSettingsDraft(saved);
        assertEquals(ui.fullscreen(), UiSettingsProjection.from(saved).config().fullscreen());
        assertEquals(ui.fullscreen(), draft.ui().fullscreen());
        draft.reset(UiSettingsProjection.Group.FULLSCREEN);
        assertEquals(GuideUiConfig.Fullscreen.defaults(), draft.ui().fullscreen());
        assertTrue(draft.animationsEnabled());
        assertTrue(draft.dirty());
        var latest = saved.withAssistantName("Player name").withDebugMode(true);
        var candidate = draft.candidate(latest);
        assertEquals(GuideUiConfig.Fullscreen.defaults(), candidate.ui().fullscreen());
        assertTrue(candidate.animationsEnabled());
        assertEquals(ui.hud(), candidate.ui().hud());
        assertEquals(ui.notifications(), candidate.ui().notifications());
        assertEquals(latest.assistantName(), candidate.assistantName());
        assertTrue(candidate.debugMode());
        assertEquals(ui, saved.ui(), "Reset remains an in-memory draft");
        assertFalse(saved.animationsEnabled());
    }

    @Test
    void fullscreenSettingsOfferNoToolFoldToggle() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java"));
        String fullscreen = source.substring(source.indexOf("private void addUiPage()"),
                source.indexOf("case HUD:", source.indexOf("private void addUiPage()")));
        assertTrue(fullscreen.contains("uiButton(\"density\""));
        assertTrue(fullscreen.contains("uiToggle(\"session_rail\""));
        assertTrue(fullscreen.contains("uiButton(\"theme\""));
        assertTrue(fullscreen.contains("uiToggle(\"animations\""));
        assertFalse(fullscreen.contains("tools_fold"));
        assertFalse(source.contains("toolsCollapsed"));
    }
}
