package dev.openallay.client.voice;

import java.util.Objects;

/** Safe player-facing voice feedback. Unknown backend codes never become UI text. */
public final class VoiceStatusPresentation {
    private static final String PREFIX = "screen.openallay.voice.feedback.";
    private VoiceStatusPresentation() {}

    public record Notice(String translationKey, String actionTranslationKey, boolean error) {
        public Notice {
            Objects.requireNonNull(translationKey, "translationKey");
            Objects.requireNonNull(actionTranslationKey, "actionTranslationKey");
        }
    }

    public static Notice describe(VoiceRuntime.Status status) {
        Objects.requireNonNull(status, "status");
        if (status.active()) return notice(status.state().name().toLowerCase(java.util.Locale.ROOT), switch (status.state()) {
            case RECORDING -> "release_to_finish";
            case TRANSCRIBING -> "wait_or_cancel";
            default -> "hold_to_speak";
        }, false);
        return describeCode(status.code());
    }

    public static Notice describeCode(String code) {
        if (code == null) return notice("failed", "retry", true);
        return switch (code) {
            case "idle", "voice_closed" -> notice("idle", "", false);
            case "starting" -> notice("starting", "hold_to_speak", false);
            case "recording" -> notice("recording", "release_to_finish", false);
            case "transcribing" -> notice("transcribing", "wait_or_cancel", false);
            case "draft_inserted" -> notice("draft_inserted", "review_draft", false);
            case "draft_pending" -> notice("draft_pending", "review_pending", false);
            case "draft_rejected" -> notice("draft_rejected", "retry_current_session", true);
            case "microphone_launcher_unprepared" -> notice("launcher_unprepared", "launcher_setup", true);
            case "microphone_denied" -> notice("permission_denied", "system_permission", true);
            case "microphone_permission_unavailable" -> notice("permission_unavailable", "check_launcher", true);
            case "microphone_device_unavailable", "device_broken" -> notice("device_unavailable", "choose_device", true);
            case "microphone_format_unsupported" -> notice("format_unsupported", "choose_device", true);
            case "microphone_backend_unavailable" -> notice("capture_unavailable", "check_audio_runtime", true);
            case "microphone_open_failed" -> notice("microphone_open_failed", "check_device", true);
            case "model_not_installed" -> notice("model_missing", "install_model", true);
            case "model_invalid", "model_integrity" -> notice("model_invalid", "replace_model", true);
            case "native_unavailable", "runtime_invalid" -> notice("runtime_unavailable", "import_runtime", true);
            case "unsupported_platform" -> notice("unsupported_platform", "supported_platform", true);
            case "native_failed" -> notice("native_failed", "check_model_runtime", true);
            case "native_timeout" -> notice("native_timeout", "shorter_clip", true);
            case "unsupported_language" -> notice("unsupported_language", "change_language", true);
            case "empty_audio", "no_speech", "voice_empty_transcript" -> notice("no_speech", "speak_again", true);
            case "voice_credential_unavailable" -> notice("credential_unavailable", "configure_http_key", true);
            case "voice_http_error" -> notice("http_error", "check_http_settings", true);
            case "voice_timeout" -> notice("http_timeout", "check_connection", true);
            case "voice_transport_error" -> notice("transport_error", "check_connection", true);
            case "voice_invalid_request" -> notice("invalid_request", "check_http_settings", true);
            case "voice_invalid_response", "voice_response_too_large" -> notice("invalid_response", "check_http_settings", true);
            case "invalid_voice_config", "voice_save_failed", "voice_settings_failed" -> notice("settings_failed", "check_voice_settings", true);
            case "model_downloading", "model_download_busy" -> notice("model_downloading", "wait_or_cancel", false);
            case "model_download_failed" -> notice("model_download_failed", "download_again", true);
            case "model_download_cancelled" -> notice("download_cancelled", "", false);
            case "runtime_imported", "voice_saved" -> notice("settings_saved", "", false);
            case "cancelled_feedback_hidden" -> notice("feedback_hidden", "show_feedback", false);
            case "cancelled_focus_lost" -> notice("focus_lost", "retry", false);
            case "cancelled_screen_closed", "cancelled_disconnected", "cancelled_key_lost", "cancelled_user",
                    "cancelled_limit", "cancelled_device_broken" -> notice("cancelled", "retry", false);
            case "no_session" -> notice("no_session", "open_session", true);
            default -> notice("failed", "retry", true);
        };
    }

    private static Notice notice(String message, String action, boolean error) {
        return new Notice(PREFIX + message, action.isEmpty() ? "" : PREFIX + "action." + action, error);
    }
}
