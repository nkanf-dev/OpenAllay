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
    void previewBudgetSupportsAutoAndEighteenLinesWithoutBecomingAReplyLimit() {
        assertEquals(18, HUD.maxReplyLines());
        assertEquals(0, HUD.withContent(0, true, true).maxReplyLines());
        assertEquals(80, HUD.withContent(80, true, true).maxReplyLines());
        assertThrows(IllegalArgumentException.class, () -> HUD.withContent(-1, true, true));
        assertThrows(IllegalArgumentException.class, () -> HUD.withContent(81, true, true));
        var semantic = dev.openallay.guide.semantic.SemanticDocument.of(java.util.List.of(
                new dev.openallay.guide.semantic.SemanticBlock.Paragraph("a".repeat(64), java.util.List.of(
                        new dev.openallay.guide.semantic.SemanticInline.Text("b".repeat(64), "full detail ".repeat(2000))))), java.util.List.of());
        var assistant = new dev.openallay.guide.ui.GuideUiRow.Assistant(java.util.UUID.randomUUID(), 0,
                semantic.fallbackText(), semantic, false, java.util.List.of());
        var view = new GuideHudView(HUD.withContent(1, true, false), "Allay", "main", "tiny preview", "", null, 0,
                java.util.List.of(assistant), GuideUiConfig.Fullscreen.defaults(), true);
        assertTrue(view.hasContent());
        assertEquals(semantic.fallbackText(), ((dev.openallay.guide.ui.GuideUiRow.Assistant) view.rows().getFirst()).text());
        assertThrows(UnsupportedOperationException.class, () -> view.rows().clear());
    }

    @Test
    void fallbackSourcesAreNotTruncatedButTaskCountsRemainValidated() {
        String completed = "x".repeat(10000) + " completed tail";
        String streaming = "😀e\u0301".repeat(10000) + " streaming tail";
        var view = new GuideHudView(HUD.withContent(0, true, true), "Allay", "main",
                completed, streaming, null, 0);
        assertEquals(completed, view.latestReply());
        assertEquals(streaming, view.streamingPreview());
        assertTrue(view.hasContent());
        assertThrows(NullPointerException.class,
                () -> new GuideHudView(HUD, "Allay", "main", null, "", null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new GuideHudView(HUD, "Allay", "main", "", "", null, -1));
    }
}
