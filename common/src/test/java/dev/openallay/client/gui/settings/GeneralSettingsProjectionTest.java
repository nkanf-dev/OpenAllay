package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.ui.GuideDisplayConfig;
import org.junit.jupiter.api.Test;

final class GeneralSettingsProjectionTest {
    @Test
    void everyGeneralActionPreservesNewUiSettings() {
        var config = GuideDisplayConfig.defaults().withUi(dev.openallay.guide.ui.GuideUiConfig.defaults()
                .withHud(dev.openallay.guide.ui.GuideUiConfig.Hud.defaults().withEnabled(true).withBackgroundOpacity(.25))
                .withNotifications(dev.openallay.guide.ui.GuideUiConfig.Notifications.defaults().withEnabled(true)));
        var projection = GeneralSettingsProjection.from(config);
        assertEquals(config.ui(), projection.toggleDebug().ui());
        assertEquals(config.ui(), projection.toggleAnimations().ui());
        assertEquals(config.ui(), projection.renameAssistant("名字").ui());
    }

    @Test
    void displayControlsDefaultSafelyAndToggleIndependently() {
        GeneralSettingsProjection projection = GeneralSettingsProjection.from(new GuideDisplayConfig(
                false, true, "小羽"));

        assertEquals("小羽", projection.assistantName());
        assertFalse(projection.debugMode());
        assertTrue(projection.animationsEnabled());
        assertTrue(projection.debugStatusKey().contains("disabled"));
        assertTrue(projection.narrationKey().contains("general"));
        assertTrue(projection.toggleDebug().debugMode());
        assertTrue(projection.toggleDebug().animationsEnabled());
        assertEquals("小羽", projection.toggleDebug().assistantName());
        assertFalse(projection.toggleAnimations().animationsEnabled());
        assertFalse(projection.toggleAnimations().debugMode());
        assertEquals("小羽", projection.toggleAnimations().assistantName());
        assertEquals("新名字", projection.renameAssistant(" 新名字 ").assistantName());
    }
}
