package dev.openallay.guide.ui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.GuideRequestProgress;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.guide.ui.GuideUiProgress;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class GuideHudViewTest {
    private static final GuideUiConfig.Hud HUD = GuideUiConfig.Hud.defaults().withEnabled(true);

    @Test
    void emptyViewRetainsDisplayPreferencesButHasNoContent() {
        GuideDisplayConfig config = GuideDisplayConfig.defaults().withAssistantName("Allay")
                .withUi(GuideUiConfig.defaults().withHud(HUD));
        GuideHudView empty = GuideHudView.empty(config);
        assertEquals(HUD, empty.hud());
        assertEquals("Allay", empty.assistantName());
        assertEquals("", empty.selectedSession());
        assertFalse(empty.hasContent());
    }

    @Test
    void replyProgressAndOtherTaskCountAreIndependentContentSignals() {
        assertTrue(new GuideHudView(HUD, "Allay", "main", "reply", "", null, 0).hasContent());
        assertTrue(new GuideHudView(HUD, "Allay", "main", "", "preview", null, 0).hasContent());
        assertTrue(new GuideHudView(HUD, "Allay", "main", "", "",
                GuideUiProgress.from(GuideRequestProgress.start(Instant.EPOCH)), 0).hasContent());
        assertTrue(new GuideHudView(HUD, "Allay", "main", "", "", null, 1).hasContent());
        assertFalse(new GuideHudView(HUD, "Allay", "main", " ", "\n", null, 0).hasContent());
    }

    @Test
    void renderViewCannotCarryOversizedPreviewsOrNegativeTaskCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new GuideHudView(HUD, "Allay", "main", "x".repeat(513), "", null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new GuideHudView(HUD, "Allay", "main", "", "😀".repeat(513), null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new GuideHudView(HUD, "Allay", "main", "", "", null, -1));
    }
}
