package dev.openallay.client.voice;

import dev.openallay.tool.ToolResult;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Actions run I/O on the owned worker. Reading view never loads natives or accesses a microphone. */
public interface VoiceSettingsActions {
    VoiceSettingsView view();
    /** Stable managed runtime root containing installed license and source notices. */
    Path runtimeNoticesDirectory();
    CompletableFuture<ToolResult<VoiceConfig>> update(VoiceConfig candidate);
    /** Saves the whole candidate and optional replacement together. Takes and clears the supplied chars. */
    CompletableFuture<ToolResult<VoiceConfig>> update(VoiceConfig candidate, char[] replacement);
    CompletableFuture<ToolResult<VoiceConfig>> reload();
    CompletableFuture<ToolResult<VoiceConfig>> importModel(Path directory);
    CompletableFuture<ToolResult<VoiceConfig>> importRuntime(Path directory);
    CompletableFuture<ToolResult<VoiceConfig>> downloadDefaultModel();
    void cancelDownload();
    CompletableFuture<ToolResult<VoiceConfig>> setApiKey(char[] key);
    CompletableFuture<ToolResult<java.util.List<AudioCapture.Device>>> refreshDevices();
}
