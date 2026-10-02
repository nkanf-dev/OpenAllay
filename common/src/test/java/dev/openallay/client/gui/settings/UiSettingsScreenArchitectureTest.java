package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class UiSettingsScreenArchitectureTest {
    @Test
    void slidersPreviewInMemoryAndDoNotSaveOrRebuildWidgets() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java"));
        String slider = block(source, "private static final class UiSlider");
        assertTrue(slider.contains("changed.accept(actual())"));
        assertFalse(slider.contains("saveDisplay"));
        assertFalse(slider.contains("rebuildWidgets"));
        String preview = source.substring(source.indexOf("private void previewHud"),
                source.indexOf("private void changeHud"));
        assertTrue(preview.contains("uiDraft.preview"));
        assertFalse(preview.contains("saveDisplay"));
        assertFalse(preview.contains("rebuildWidgets"));
        String apply = source.substring(source.indexOf("private void applyUi()"),
                source.indexOf("private void renderUi"));
        assertEquals(1, apply.split("saveDisplay", -1).length - 1);
        assertTrue(source.contains("withUiActions"));
        assertTrue(source.contains("withVoiceActions"));
        assertTrue(source.contains("previewNotification"));        assertFalse(source.contains("cycleSection"));
    }

    /** These production blocks contain no literal braces; avoid a later feature's method as an end marker. */
    private static String block(String source, String declaration) {
        int start = source.indexOf(declaration);
        assertTrue(start >= 0, declaration);
        int depth = 0;
        boolean opened = false;
        for (int index = start; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
                opened = true;
            } else if (character == '}') {
                depth--;
                if (opened && depth == 0) return source.substring(start, index + 1);
            }
        }
        throw new AssertionError("Unclosed source block: " + declaration);
    }
}
