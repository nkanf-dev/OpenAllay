package dev.openallay.guide.ui.hud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.ui.GuideUiConfig;
import org.junit.jupiter.api.Test;

final class GuideHudVisibilityTest {
    private static final GuideUiConfig.Hud HUD = GuideUiConfig.Hud.defaults().withEnabled(true);

    @Test
    void requiresEnabledWorldAndPlayerAndAlwaysRespectsF1AndOverlays() {
        assertTrue(visible(HUD, true, true, false, false, false, false));
        assertFalse(visible(HUD.withEnabled(false), true, true, false, false, false, false));
        assertFalse(visible(HUD, false, true, false, false, false, false));
        assertFalse(visible(HUD, true, false, false, false, false, false));
        assertFalse(visible(HUD, true, true, true, false, false, false));
        assertFalse(visible(HUD, true, true, false, false, false, true));
        GuideUiConfig.Hud permissive = HUD.withVisibility(false, false);
        assertFalse(visible(permissive, true, true, true, true, true, false));
        assertFalse(visible(permissive, true, true, false, true, true, true));
    }

    @Test
    void debugAndOtherScreenGatesFollowTheirOwnPreferences() {
        assertFalse(visible(HUD, true, true, false, true, false, false));
        assertFalse(visible(HUD, true, true, false, false, true, false));
        assertTrue(visible(HUD.withVisibility(false, true), true, true, false, true, false, false));
        assertTrue(visible(HUD.withVisibility(true, false), true, true, false, false, true, false));
        assertTrue(visible(HUD.withVisibility(false, false), true, true, false, true, true, false));
    }

    @Test
    void transparentBackgroundAndCollapsedContentDoNotChangeVisibilityPolicy() {
        assertTrue(visible(HUD.withBackgroundOpacity(0).withCollapsed(true),
                true, true, false, false, false, false));
    }

    private static boolean visible(GuideUiConfig.Hud hud, boolean world, boolean player,
            boolean hidden, boolean debug, boolean screen, boolean overlay) {
        return GuideHudVisibility.isVisible(hud,
                new GuideHudVisibility.Context(world, player, hidden, debug, screen, overlay));
    }
}
