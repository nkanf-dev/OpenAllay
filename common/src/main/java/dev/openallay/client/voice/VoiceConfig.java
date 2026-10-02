package dev.openallay.client.voice;

import dev.openallay.model.config.CredentialReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/** Latest-only client preferences. Only references, never secrets, are persisted. */
public record VoiceConfig(boolean enabled, Backend backend, String deviceId, int maxClipSeconds,
        String language, int cpuThreads, String nativeModelDirectory,
        URI httpBaseUrl, String httpModel, CredentialReference credential) {
    public enum Backend { NATIVE, HTTP }
    public VoiceConfig {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(nativeModelDirectory, "nativeModelDirectory");
        Objects.requireNonNull(httpBaseUrl, "httpBaseUrl");
        Objects.requireNonNull(httpModel, "httpModel");
        if (deviceId.isBlank() || deviceId.length() > 512) throw new IllegalArgumentException("deviceId");
        if (maxClipSeconds < 1 || maxClipSeconds > PcmClip.MAX_SECONDS) throw new IllegalArgumentException("maxClipSeconds");
        if (!language.matches("auto|[a-z]{2,3}(-[A-Z]{2})?")) throw new IllegalArgumentException("language");
        if (cpuThreads < 1 || cpuThreads > 8) throw new IllegalArgumentException("cpuThreads");
        if (!nativeModelDirectory.isEmpty()) Path.of(nativeModelDirectory);
        if (!java.util.Set.of("http", "https").contains(httpBaseUrl.getScheme())
                || httpBaseUrl.getHost() == null || httpBaseUrl.getUserInfo() != null
                || httpBaseUrl.getFragment() != null || httpBaseUrl.getQuery() != null) throw new IllegalArgumentException("httpBaseUrl");
        if (httpModel.isBlank() || httpModel.length() > 256 || httpModel.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException("httpModel");
    }
    public static VoiceConfig defaults() {
        return new VoiceConfig(false, Backend.NATIVE, "default", 20, "auto",
                Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors())), "",
                URI.create("http://127.0.0.1:8080/v1"), "whisper-1", null);
    }
    public VoiceConfig withEnabled(boolean value) { return new VoiceConfig(value, backend, deviceId, maxClipSeconds, language, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, credential); }
    public VoiceConfig withBackend(Backend value) { return new VoiceConfig(enabled, value, deviceId, maxClipSeconds, language, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, credential); }
    public VoiceConfig withDevice(String value) { return new VoiceConfig(enabled, backend, value, maxClipSeconds, language, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, credential); }
    public VoiceConfig withLimits(int seconds, int threads) { return new VoiceConfig(enabled, backend, deviceId, seconds, language, threads, nativeModelDirectory, httpBaseUrl, httpModel, credential); }
    public VoiceConfig withLanguage(String value) { return new VoiceConfig(enabled, backend, deviceId, maxClipSeconds, value, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, credential); }
    public VoiceConfig withModelDirectory(Path value) { return new VoiceConfig(enabled, backend, deviceId, maxClipSeconds, language, cpuThreads, value.toAbsolutePath().normalize().toString(), httpBaseUrl, httpModel, credential); }
    public VoiceConfig withHttp(URI url, String model) { return new VoiceConfig(enabled, backend, deviceId, maxClipSeconds, language, cpuThreads, nativeModelDirectory, url, model, credential); }
    public VoiceConfig withCredential(CredentialReference value) { return new VoiceConfig(enabled, backend, deviceId, maxClipSeconds, language, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, value); }
}
