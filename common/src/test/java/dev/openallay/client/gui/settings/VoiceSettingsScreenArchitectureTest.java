package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VoiceSettingsScreenArchitectureTest {
    @Test
    void realVoiceActionsAreSeparateFromDisplaySaveAndAgentSubmission() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java"));
        String voice = source.substring(source.indexOf("private void resetVoiceDraft"),
                source.indexOf("private void addModelsPage"));
        assertTrue(voice.contains("voiceActions.importModel(Path.of(voiceModelPath))"));
        assertTrue(voice.contains("voiceActions.importRuntime(Path.of(voiceRuntimePath))"));
        assertTrue(voice.contains("voiceActions.downloadDefaultModel()"));
        assertTrue(voice.contains("voiceActions::cancelDownload"));
        assertTrue(voice.contains("voiceActions.refreshDevices()"));
        assertTrue(voice.contains("voiceActions.update(submitted, voiceApiKeyDraft.isBlank()"));
        assertTrue(voice.contains("voiceApiKeyDraft.toCharArray()"));
        assertTrue(voice.contains("voiceButton(\"store_api_key\", Component.empty(), x, y, w, this::applyVoice)"));
        assertTrue(voice.contains("net.minecraft.util.Util.getPlatform().openPath(voiceActions.runtimeNoticesDirectory())"));
        assertFalse(voice.contains("saveDisplay"));
        assertFalse(voice.contains("ProcessBuilder"));
        assertFalse(voice.contains("Runtime.getRuntime().exec"));
        assertFalse(voice.contains(".ask("));
        assertFalse(voice.contains("System.getenv"));
        assertFalse(voice.contains("voiceModelPath).open"));
        assertTrue(source.contains("void previewNotification(GuideUiConfig.Notifications config)"));
        assertTrue(source.contains("GuideUiConfig.Notifications notifications = uiDraft.ui().notifications()"));
    }
}
