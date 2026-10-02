package dev.openallay.client.voice;

import java.util.List;

public record VoiceSettingsView(VoiceConfig config, List<AudioCapture.Device> devices,
        boolean busy, boolean modelReady, String modelName, String statusCode, long downloadedBytes, long totalBytes) {
    public VoiceSettingsView { devices = List.copyOf(devices); }
}
