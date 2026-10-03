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
    @Test void missingCaptureProviderIsNotPresentedAsAnUnpluggedMicrophoneOrDeniedPermission() {
        var backend = VoiceStatusPresentation.describeCode("microphone_backend_unavailable");
        var device = VoiceStatusPresentation.describeCode("microphone_device_unavailable");
        var denied = VoiceStatusPresentation.describeCode("microphone_denied");
        assertTrue(backend.translationKey().endsWith("capture_unavailable"));
        assertTrue(backend.actionTranslationKey().endsWith("check_audio_runtime"));
        assertNotEquals(backend.translationKey(), device.translationKey());
        assertNotEquals(backend.actionTranslationKey(), denied.actionTranslationKey());
        assertTrue(backend.error());
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
    @Test void sentQueuedAndRefusedDeliveryNeverClaimDraftInsertionOrTaskCompletion() {
        var sent = VoiceStatusPresentation.describeCode("voice_sent");
        var queued = VoiceStatusPresentation.describeCode("voice_queued");
        var failed = VoiceStatusPresentation.describeCode("voice_send_failed");
        assertTrue(sent.translationKey().endsWith("voice_sent")); assertFalse(sent.error());
        assertTrue(queued.translationKey().endsWith("voice_queued")); assertFalse(queued.error());
        assertNotEquals(sent.translationKey(), queued.translationKey());
        assertEquals("", sent.actionTranslationKey()); assertEquals("", queued.actionTranslationKey());
        assertTrue(failed.error()); assertTrue(failed.actionTranslationKey().endsWith("review_pending"));
        assertTrue(VoiceStatusPresentation.describeCode("voice_send_rejected").error());
        var delivering = VoiceStatusPresentation.describe(new VoiceRuntime.Status(
                VoiceRuntime.State.DELIVERING, "untrusted detail", 0, 0, "", null, null));
        assertTrue(delivering.translationKey().endsWith("delivering"));
        assertTrue(delivering.actionTranslationKey().endsWith("wait_or_cancel")); assertFalse(delivering.error());
    }
    @Test void livePhaseDoesNotExposeAnUnexpectedBackendCode() {
        var status = new VoiceRuntime.Status(VoiceRuntime.State.RECORDING, "unexpected /private/path", 3000, 30000, "", null, null);
        var shown = VoiceStatusPresentation.describe(status);
        assertEquals("screen.openallay.voice.feedback.recording", shown.translationKey());
        assertFalse(shown.error());
    }
}
