package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class GuideUiConfigTest {
    @Test
    void hudAndNotificationsAreIndependentAndAlphaDoesNotDimText() {
        var defaults = GuideUiConfig.defaults();
        var notificationOnly = defaults.withNotifications(defaults.notifications().withEnabled(true));
        assertFalse(notificationOnly.hud().enabled());
        assertTrue(notificationOnly.notifications().enabled());
        var hudOnly = defaults.withHud(defaults.hud().withEnabled(true));
        assertTrue(hudOnly.hud().enabled());
        assertFalse(hudOnly.notifications().enabled());
        for (double opacity : new double[] {0, .2, 1}) {
            var hud = defaults.hud().withBackgroundOpacity(opacity);
            assertEquals((int) Math.round(opacity * 255), hud.backgroundArgb(0x181B22) >>> 24);
            assertEquals(255, hud.textArgb(0xE8EDF2) >>> 24);
        }
    }

    @Test
    void recordConstructorsRejectEveryOutOfRangeAndNonFiniteValue() {
        var hud = GuideUiConfig.Hud.defaults();
        assertThrows(IllegalArgumentException.class, () -> hud.withPlacement(hud.anchor(), -4097, 0, 280, 88, 1));
        assertThrows(IllegalArgumentException.class, () -> hud.withPlacement(hud.anchor(), 0, 4097, 280, 88, 1));
        for (int width : new int[] {159, 481})
            assertThrows(IllegalArgumentException.class, () -> hud.withPlacement(hud.anchor(), 0, 0, width, 88, 1));
        for (int height : new int[] {43, 241})
            assertThrows(IllegalArgumentException.class, () -> hud.withPlacement(hud.anchor(), 0, 0, 280, height, 1));
        for (double scale : new double[] {.74, 1.76, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> hud.withPlacement(hud.anchor(), 0, 0, 280, 88, scale));
        for (double alpha : new double[] {-.01, 1.01, Double.NaN, Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> hud.withBackgroundOpacity(alpha));
        for (int lines : new int[] {0, 11})
            assertThrows(IllegalArgumentException.class, () -> hud.withContent(lines, true, false));
        for (int duration : new int[] {2, 16})
            assertThrows(IllegalArgumentException.class, () -> GuideUiConfig.Notifications.defaults().withDurationSeconds(duration));
        assertThrows(NullPointerException.class, () -> hud.withPlacement(null, 0, 0, 280, 88, 1));
        assertThrows(NullPointerException.class, () -> new GuideUiConfig.Fullscreen(null, true, true, GuideUiConfig.Theme.CHARCOAL));
        assertThrows(NullPointerException.class, () -> GuideUiConfig.Notifications.defaults().withPolicy(null));
    }

    @Test
    void allAnchorsHaveDeterministicFactorsAndWithMethodsPreserveUnchangedFields() {
        assertEquals(0, GuideUiConfig.Anchor.TOP_LEFT.xFactor());
        assertEquals(.5, GuideUiConfig.Anchor.CENTER.xFactor());
        assertEquals(.5, GuideUiConfig.Anchor.CENTER.yFactor());
        assertEquals(1, GuideUiConfig.Anchor.BOTTOM_RIGHT.xFactor());
        assertEquals(1, GuideUiConfig.Anchor.BOTTOM_RIGHT.yFactor());
        var configured = new GuideDisplayConfig(false, true, "OpenAllay", GuideUiConfig.defaults()
                .withHud(GuideUiConfig.Hud.defaults().withEnabled(true).withBackgroundOpacity(.15))
                .withNotifications(GuideUiConfig.Notifications.defaults().withEnabled(true).withDurationSeconds(15)));
        assertEquals(configured.ui(), configured.withAssistantName("小羽").ui());
        assertEquals(configured.ui(), configured.withDebugMode(true).ui());
        assertEquals(configured.ui(), configured.withAnimationsEnabled(false).ui());
        assertEquals(configured.assistantName(), configured.withUi(GuideUiConfig.defaults()).assistantName());
    }
}
