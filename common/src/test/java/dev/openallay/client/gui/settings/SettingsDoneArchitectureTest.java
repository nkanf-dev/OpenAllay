package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SettingsDoneArchitectureTest {
    @Test
    void realDoneCaptureRoutesSaveEditorsWithoutProbingInstallingOrOpeningAudio() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java"));
        String done = source.substring(source.indexOf("private void done()"), source.indexOf("private void saveEditor"));
        assertTrue(done.contains("captureDraft()"));
        assertTrue(done.contains("saveGeneral(true)"));
        assertTrue(done.contains("saveModel(true)"));
        assertTrue(done.contains("saveUi(true)"));
        assertTrue(done.contains("saveVoice(true)"));
        assertFalse(done.contains("testConnection"));
        assertFalse(done.contains("download"));
        assertFalse(done.contains("refreshDevices"));
        assertFalse(done.contains("install" + "Community"));
        assertFalse(done.contains(".ask("));
        assertTrue(source.contains("public void onClose() { done(); }"));
        String capture = source.substring(source.indexOf("private void captureDraft()"), source.indexOf("private ModelReasoningSettingsProjection"));
        assertTrue(capture.contains("pendingApiKey = apiKey.getValue()"));
        assertTrue(capture.contains("assistantNameDraft = assistantName.getValue()"));
        assertTrue(capture.contains("voiceFieldValue(\"http_url\", voiceHttpUrl)"));
        assertTrue(capture.contains("voiceFieldValue(\"api_key\", voiceApiKeyDraft)"));
        assertTrue(source.contains("uiSlider(\"reply_lines\", hud.maxReplyLines(), 0, 80"));
        assertTrue(source.contains("screen.openallay.settings.ui.reply_lines.auto"));
    }

    @Test
    void returnedEditorCandidateCannotBeRecapturedFromRemovedParentFields() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java"));
        String opening = source.substring(source.indexOf("private void editHud()"), source.indexOf("private void applyUiCandidate"));
        assertTrue(opening.contains("captureDraft()"));
        assertTrue(opening.contains("if (!validateUiDraft()) return"));
        String adopt = source.substring(source.indexOf("private void applyUiCandidate"), source.indexOf("private boolean validateUiDraft"));
        assertTrue(adopt.contains("GuideDisplayConfig candidate = service.snapshot().display().withUi(returned.ui())"));
        assertTrue(adopt.contains("candidate.equals(service.snapshot().display())"));
        assertFalse(adopt.contains("candidate = snapshot.display().withUi(returned.ui())"));
        assertTrue(adopt.contains("uiDraft.adopt(candidate)"));
        assertTrue(adopt.contains("synchronizeUiInteger(\"offset_x\", candidate.ui().hud().offsetX())"));
        assertTrue(adopt.contains("synchronizeUiInteger(\"offset_y\", candidate.ui().hud().offsetY())"));
        assertTrue(adopt.contains("field.setValue(text)"));
        assertTrue(adopt.contains("service.saveDisplay(candidate)"));
        assertFalse(adopt.contains("captureDraft()"));
        assertFalse(adopt.contains("validateUiDraft()"));
    }

    @Test
    void e2ePortsAreExplicitlyGatedAndPressRealButtonsRatherThanAssigningSettings() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java"));
        String ports = source.substring(source.indexOf("public void e2eChooseSection"), source.indexOf("public E2eSettingsState"));
        assertTrue(ports.contains("requireE2eControls()"));
        assertTrue(ports.contains("GuideNativeInput.press(button, GuideNativeInput.keyEvent(InputConstants.KEY_RETURN, 0))"));
        String binding = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/GuideNativeInput.java"));
        assertTrue(binding.contains("public static void press(Button button, GuideInputKey event)"));
        assertTrue(binding.contains("button.onPress(nativeKey(event))"));
        assertFalse(ports.contains("service.save"));
        assertFalse(ports.contains("uiDraft.preview"));
        assertFalse(ports.contains("section = target"));
    }
}
