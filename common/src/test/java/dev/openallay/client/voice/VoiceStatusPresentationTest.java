package dev.openallay.client.voice;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VoiceStatusPresentationTest {
    @Test void launcherDeclarationAndSystemDeniedAreDifferentActionableErrors() {
        var launcher = VoiceStatusPresentation.describeCode("microphone_launcher_unprepared");
        var denied = VoiceStatusPresentation.describeCode("microphone_denied");
        assertNotEquals(launcher.translationKey(), denied.translationKey());
        assertTrue(launcher.actionTranslationKey().endsWith("launcher_setup"));
        assertTrue(denied.actionTranslationKey().endsWith("system_permission"));
        assertTrue(launcher.error());
        assertTrue(denied.error());
    }
    @Test void backendAndCaptureFailuresExplainTheNextSafeAction() {
        assertTrue(VoiceStatusPresentation.describeCode("model_not_installed").actionTranslationKey().endsWith("install_model"));
        assertTrue(VoiceStatusPresentation.describeCode("native_unavailable").actionTranslationKey().endsWith("import_runtime"));
        assertTrue(VoiceStatusPresentation.describeCode("voice_credential_unavailable").actionTranslationKey().endsWith("configure_http_key"));
        assertTrue(VoiceStatusPresentation.describeCode("microphone_open_failed").actionTranslationKey().endsWith("check_device"));
        assertTrue(VoiceStatusPresentation.describeCode("device_broken").actionTranslationKey().endsWith("choose_device"));
        assertTrue(VoiceStatusPresentation.describeCode("unsupported_platform").actionTranslationKey().endsWith("supported_platform"));
    }
    @Test void unknownCodesNeverBecomePlayerTextOrTranslationKeys() {
        for (String raw : new String[]{null, "Bearer private-key /Users/name/model", "https://secret.example?token=key", "NullPointerException"}) {
            var shown = VoiceStatusPresentation.describeCode(raw);
            assertEquals("screen.openallay.voice.feedback.failed", shown.translationKey());
            assertEquals("screen.openallay.voice.feedback.action.retry", shown.actionTranslationKey());
            assertTrue(shown.error());
        }
    }
    @Test void acceptedAndPendingTranscriptsHaveDifferentReviewHints() {
        assertTrue(VoiceStatusPresentation.describeCode("draft_inserted").actionTranslationKey().endsWith("review_draft"));
        assertTrue(VoiceStatusPresentation.describeCode("draft_pending").actionTranslationKey().endsWith("review_pending"));
        assertFalse(VoiceStatusPresentation.describeCode("draft_pending").error());
    }
    @Test void livePhaseDoesNotExposeAnUnexpectedBackendCode() {
        var status = new VoiceRuntime.Status(VoiceRuntime.State.RECORDING, "unexpected /private/path", 3000, 30000, "", null);
        var shown = VoiceStatusPresentation.describe(status);
        assertEquals("screen.openallay.voice.feedback.recording", shown.translationKey());
        assertFalse(shown.error());
    }
}
