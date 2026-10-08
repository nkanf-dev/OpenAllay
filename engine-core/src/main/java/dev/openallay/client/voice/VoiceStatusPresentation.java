package dev.openallay.client.voice;

import java.util.Objects;

/** Safe player-facing voice feedback. Unknown backend codes never become UI text. */
public final class VoiceStatusPresentation {
    private static final String PREFIX = "screen.openallay.voice.feedback.";
    private VoiceStatusPresentation() {}

    @dev.openallay.value.ValueType(Notice.ValueSchemaProvider.class)
public static final class Notice {
    private final String translationKey;
    private final String actionTranslationKey;
    private final boolean error;
    public Notice(String translationKey, String actionTranslationKey, boolean error) {

            Objects.requireNonNull(translationKey, "translationKey");
            Objects.requireNonNull(actionTranslationKey, "actionTranslationKey");

        this.translationKey = translationKey;
        this.actionTranslationKey = actionTranslationKey;
        this.error = error;
    }
    public String translationKey() { return translationKey; }
    public String actionTranslationKey() { return actionTranslationKey; }
    public boolean error() { return error; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Notice)) return false;
        Notice that = (Notice) other;
        return java.util.Objects.equals(translationKey, that.translationKey) && java.util.Objects.equals(actionTranslationKey, that.actionTranslationKey) && error == that.error;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(translationKey);
        hash = 31 * hash + java.util.Objects.hashCode(actionTranslationKey);
        hash = 31 * hash + Boolean.hashCode(error);
        return hash;
    }
    @Override public String toString() { return "Notice[translationKey=" + translationKey + ", actionTranslationKey=" + actionTranslationKey + ", error=" + error + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Notice> schema() {
            return new dev.openallay.value.ValueSchema<>(Notice.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Notice>>asList(new dev.openallay.value.ValueSchema.Component<>(Notice.class, "translationKey", Notice::translationKey), new dev.openallay.value.ValueSchema.Component<>(Notice.class, "actionTranslationKey", Notice::actionTranslationKey), new dev.openallay.value.ValueSchema.Component<>(Notice.class, "error", Notice::error)), arguments -> new Notice((String) arguments[0], (String) arguments[1], (Boolean) arguments[2]));
        }
    }
}

    public static Notice describe(VoiceRuntime.Status status) {
        Objects.requireNonNull(status, "status");
        if (status.active()) {
final java.lang.String $oaSwitch0_exit_result_prior0 = status.state().name().toLowerCase(java.util.Locale.ROOT);
java.lang.String $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((status.state())) {
case RECORDING:
{
$oaSwitch0_exit_result = "release_to_finish"; break $oaSwitch0_exit;
}
case TRANSCRIBING:
case DELIVERING:
{
$oaSwitch0_exit_result = "wait_or_cancel"; break $oaSwitch0_exit;
}
default:
{
$oaSwitch0_exit_result = "hold_to_speak"; break $oaSwitch0_exit;
}
}
}
return notice($oaSwitch0_exit_result_prior0, $oaSwitch0_exit_result, false);
}
        return describeCode(status.code());
    }

    public static Notice describeCode(String code) {
        if (code == null) return notice("failed", "retry", true);
        {
dev.openallay.client.voice.VoiceStatusPresentation.Notice $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((code)) {
case "idle":
case "voice_closed":
{
$oaSwitch1_exit_result = notice("idle", "", false); break $oaSwitch1_exit;
}
case "starting":
{
$oaSwitch1_exit_result = notice("starting", "hold_to_speak", false); break $oaSwitch1_exit;
}
case "recording":
{
$oaSwitch1_exit_result = notice("recording", "release_to_finish", false); break $oaSwitch1_exit;
}
case "transcribing":
{
$oaSwitch1_exit_result = notice("transcribing", "wait_or_cancel", false); break $oaSwitch1_exit;
}
case "voice_sending":
{
$oaSwitch1_exit_result = notice("delivering", "wait_or_cancel", false); break $oaSwitch1_exit;
}
case "voice_sent":
{
$oaSwitch1_exit_result = notice("voice_sent", "", false); break $oaSwitch1_exit;
}
case "voice_queued":
{
$oaSwitch1_exit_result = notice("voice_queued", "", false); break $oaSwitch1_exit;
}
case "voice_send_failed":
{
$oaSwitch1_exit_result = notice("voice_send_failed", "review_pending", true); break $oaSwitch1_exit;
}
case "voice_send_rejected":
{
$oaSwitch1_exit_result = notice("voice_send_rejected", "retry_current_session", true); break $oaSwitch1_exit;
}
case "draft_inserted":
{
$oaSwitch1_exit_result = notice("draft_inserted", "review_draft", false); break $oaSwitch1_exit;
}
case "draft_pending":
{
$oaSwitch1_exit_result = notice("draft_pending", "review_pending", false); break $oaSwitch1_exit;
}
case "draft_rejected":
{
$oaSwitch1_exit_result = notice("draft_rejected", "retry_current_session", true); break $oaSwitch1_exit;
}
case "microphone_launcher_unprepared":
{
$oaSwitch1_exit_result = notice("launcher_unprepared", "launcher_setup", true); break $oaSwitch1_exit;
}
case "microphone_denied":
{
$oaSwitch1_exit_result = notice("permission_denied", "system_permission", true); break $oaSwitch1_exit;
}
case "microphone_permission_unavailable":
{
$oaSwitch1_exit_result = notice("permission_unavailable", "check_launcher", true); break $oaSwitch1_exit;
}
case "microphone_device_unavailable":
case "device_broken":
{
$oaSwitch1_exit_result = notice("device_unavailable", "choose_device", true); break $oaSwitch1_exit;
}
case "microphone_format_unsupported":
{
$oaSwitch1_exit_result = notice("format_unsupported", "choose_device", true); break $oaSwitch1_exit;
}
case "microphone_backend_unavailable":
{
$oaSwitch1_exit_result = notice("capture_unavailable", "check_audio_runtime", true); break $oaSwitch1_exit;
}
case "microphone_open_failed":
{
$oaSwitch1_exit_result = notice("microphone_open_failed", "check_device", true); break $oaSwitch1_exit;
}
case "model_not_installed":
{
$oaSwitch1_exit_result = notice("model_missing", "install_model", true); break $oaSwitch1_exit;
}
case "model_invalid":
case "model_integrity":
{
$oaSwitch1_exit_result = notice("model_invalid", "replace_model", true); break $oaSwitch1_exit;
}
case "native_unavailable":
case "runtime_invalid":
{
$oaSwitch1_exit_result = notice("runtime_unavailable", "import_runtime", true); break $oaSwitch1_exit;
}
case "unsupported_platform":
{
$oaSwitch1_exit_result = notice("unsupported_platform", "supported_platform", true); break $oaSwitch1_exit;
}
case "native_failed":
{
$oaSwitch1_exit_result = notice("native_failed", "check_model_runtime", true); break $oaSwitch1_exit;
}
case "native_timeout":
{
$oaSwitch1_exit_result = notice("native_timeout", "shorter_clip", true); break $oaSwitch1_exit;
}
case "unsupported_language":
{
$oaSwitch1_exit_result = notice("unsupported_language", "change_language", true); break $oaSwitch1_exit;
}
case "empty_audio":
case "no_speech":
case "voice_empty_transcript":
{
$oaSwitch1_exit_result = notice("no_speech", "speak_again", true); break $oaSwitch1_exit;
}
case "voice_credential_unavailable":
{
$oaSwitch1_exit_result = notice("credential_unavailable", "configure_http_key", true); break $oaSwitch1_exit;
}
case "voice_http_error":
{
$oaSwitch1_exit_result = notice("http_error", "check_http_settings", true); break $oaSwitch1_exit;
}
case "voice_timeout":
{
$oaSwitch1_exit_result = notice("http_timeout", "check_connection", true); break $oaSwitch1_exit;
}
case "voice_transport_error":
{
$oaSwitch1_exit_result = notice("transport_error", "check_connection", true); break $oaSwitch1_exit;
}
case "voice_invalid_request":
{
$oaSwitch1_exit_result = notice("invalid_request", "check_http_settings", true); break $oaSwitch1_exit;
}
case "voice_invalid_response":
case "voice_response_too_large":
{
$oaSwitch1_exit_result = notice("invalid_response", "check_http_settings", true); break $oaSwitch1_exit;
}
case "invalid_voice_config":
case "voice_save_failed":
case "voice_settings_failed":
{
$oaSwitch1_exit_result = notice("settings_failed", "check_voice_settings", true); break $oaSwitch1_exit;
}
case "model_downloading":
case "model_download_busy":
{
$oaSwitch1_exit_result = notice("model_downloading", "wait_or_cancel", false); break $oaSwitch1_exit;
}
case "model_download_failed":
{
$oaSwitch1_exit_result = notice("model_download_failed", "download_again", true); break $oaSwitch1_exit;
}
case "model_download_cancelled":
{
$oaSwitch1_exit_result = notice("download_cancelled", "", false); break $oaSwitch1_exit;
}
case "runtime_imported":
case "voice_saved":
{
$oaSwitch1_exit_result = notice("settings_saved", "", false); break $oaSwitch1_exit;
}
case "cancelled_feedback_hidden":
{
$oaSwitch1_exit_result = notice("feedback_hidden", "show_feedback", false); break $oaSwitch1_exit;
}
case "cancelled_focus_lost":
{
$oaSwitch1_exit_result = notice("focus_lost", "retry", false); break $oaSwitch1_exit;
}
case "cancelled_screen_closed":
case "cancelled_disconnected":
case "cancelled_key_lost":
case "cancelled_user":
case "cancelled_limit":
case "cancelled_device_broken":
{
$oaSwitch1_exit_result = notice("cancelled", "retry", false); break $oaSwitch1_exit;
}
case "no_session":
{
$oaSwitch1_exit_result = notice("no_session", "open_session", true); break $oaSwitch1_exit;
}
default:
{
$oaSwitch1_exit_result = notice("failed", "retry", true); break $oaSwitch1_exit;
}
}
}
return $oaSwitch1_exit_result;
}
    }

    private static Notice notice(String message, String action, boolean error) {
        return new Notice(PREFIX + message, action.isEmpty() ? "" : PREFIX + "action." + action, error);
    }
}
