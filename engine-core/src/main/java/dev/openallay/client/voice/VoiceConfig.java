package dev.openallay.client.voice;

import dev.openallay.model.config.CredentialReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/** Latest-only client preferences. Only references, never secrets, are persisted. */
@dev.openallay.value.ValueType(VoiceConfig.ValueSchemaProvider.class)
public final class VoiceConfig {
    private final boolean enabled;
    private final Backend backend;
    private final String deviceId;
    private final int maxClipSeconds;
    private final String language;
    private final int cpuThreads;
    private final String nativeModelDirectory;
    private final URI httpBaseUrl;
    private final String httpModel;
    private final CredentialReference credential;
    private final GameplayAction gameplayAction;
    public VoiceConfig(boolean enabled, Backend backend, String deviceId, int maxClipSeconds, String language, int cpuThreads, String nativeModelDirectory, URI httpBaseUrl, String httpModel, CredentialReference credential, GameplayAction gameplayAction) {

        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(gameplayAction, "gameplayAction");
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
        if (httpBaseUrl.getScheme() == null
                || !java.util.Set.of("http", "https").contains(httpBaseUrl.getScheme())
                || httpBaseUrl.getHost() == null || httpBaseUrl.getUserInfo() != null
                || httpBaseUrl.getFragment() != null || httpBaseUrl.getQuery() != null) throw new IllegalArgumentException("httpBaseUrl");
        if (httpModel.isBlank() || httpModel.length() > 256 || httpModel.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException("httpModel");

        this.enabled = enabled;
        this.backend = backend;
        this.deviceId = deviceId;
        this.maxClipSeconds = maxClipSeconds;
        this.language = language;
        this.cpuThreads = cpuThreads;
        this.nativeModelDirectory = nativeModelDirectory;
        this.httpBaseUrl = httpBaseUrl;
        this.httpModel = httpModel;
        this.credential = credential;
        this.gameplayAction = gameplayAction;
    }
    public boolean enabled() { return enabled; }
    public Backend backend() { return backend; }
    public String deviceId() { return deviceId; }
    public int maxClipSeconds() { return maxClipSeconds; }
    public String language() { return language; }
    public int cpuThreads() { return cpuThreads; }
    public String nativeModelDirectory() { return nativeModelDirectory; }
    public URI httpBaseUrl() { return httpBaseUrl; }
    public String httpModel() { return httpModel; }
    public CredentialReference credential() { return credential; }
    public GameplayAction gameplayAction() { return gameplayAction; }
public enum Backend { NATIVE, HTTP }
public enum GameplayAction { SEND, DRAFT }
public static VoiceConfig defaults() {
        return new VoiceConfig(false, Backend.NATIVE, "default", 20, "auto",
                Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors())), "",
                URI.create("http://127.0.0.1:8080/v1"), "whisper-1", null, GameplayAction.SEND);
    }
public VoiceConfig withEnabled(boolean value) { return new VoiceConfig(value, backend, deviceId, maxClipSeconds, language, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, credential, gameplayAction); }
public VoiceConfig withBackend(Backend value) { return new VoiceConfig(enabled, value, deviceId, maxClipSeconds, language, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, credential, gameplayAction); }
public VoiceConfig withDevice(String value) { return new VoiceConfig(enabled, backend, value, maxClipSeconds, language, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, credential, gameplayAction); }
public VoiceConfig withLimits(int seconds, int threads) { return new VoiceConfig(enabled, backend, deviceId, seconds, language, threads, nativeModelDirectory, httpBaseUrl, httpModel, credential, gameplayAction); }
public VoiceConfig withLanguage(String value) { return new VoiceConfig(enabled, backend, deviceId, maxClipSeconds, value, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, credential, gameplayAction); }
public VoiceConfig withModelDirectory(Path value) { return new VoiceConfig(enabled, backend, deviceId, maxClipSeconds, language, cpuThreads, value.toAbsolutePath().normalize().toString(), httpBaseUrl, httpModel, credential, gameplayAction); }
public VoiceConfig withHttp(URI url, String model) { return new VoiceConfig(enabled, backend, deviceId, maxClipSeconds, language, cpuThreads, nativeModelDirectory, url, model, credential, gameplayAction); }
public VoiceConfig withCredential(CredentialReference value) { return new VoiceConfig(enabled, backend, deviceId, maxClipSeconds, language, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, value, gameplayAction); }
public VoiceConfig withGameplayAction(GameplayAction value) { return new VoiceConfig(enabled, backend, deviceId, maxClipSeconds, language, cpuThreads, nativeModelDirectory, httpBaseUrl, httpModel, credential, value); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VoiceConfig)) return false;
        VoiceConfig that = (VoiceConfig) other;
        return enabled == that.enabled && java.util.Objects.equals(backend, that.backend) && java.util.Objects.equals(deviceId, that.deviceId) && maxClipSeconds == that.maxClipSeconds && java.util.Objects.equals(language, that.language) && cpuThreads == that.cpuThreads && java.util.Objects.equals(nativeModelDirectory, that.nativeModelDirectory) && java.util.Objects.equals(httpBaseUrl, that.httpBaseUrl) && java.util.Objects.equals(httpModel, that.httpModel) && java.util.Objects.equals(credential, that.credential) && java.util.Objects.equals(gameplayAction, that.gameplayAction);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + java.util.Objects.hashCode(backend);
        hash = 31 * hash + java.util.Objects.hashCode(deviceId);
        hash = 31 * hash + Integer.hashCode(maxClipSeconds);
        hash = 31 * hash + java.util.Objects.hashCode(language);
        hash = 31 * hash + Integer.hashCode(cpuThreads);
        hash = 31 * hash + java.util.Objects.hashCode(nativeModelDirectory);
        hash = 31 * hash + java.util.Objects.hashCode(httpBaseUrl);
        hash = 31 * hash + java.util.Objects.hashCode(httpModel);
        hash = 31 * hash + java.util.Objects.hashCode(credential);
        hash = 31 * hash + java.util.Objects.hashCode(gameplayAction);
        return hash;
    }
    @Override public String toString() { return "VoiceConfig[enabled=" + enabled + ", backend=" + backend + ", deviceId=" + deviceId + ", maxClipSeconds=" + maxClipSeconds + ", language=" + language + ", cpuThreads=" + cpuThreads + ", nativeModelDirectory=" + nativeModelDirectory + ", httpBaseUrl=" + httpBaseUrl + ", httpModel=" + httpModel + ", credential=" + credential + ", gameplayAction=" + gameplayAction + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<VoiceConfig> schema() {
            return new dev.openallay.value.ValueSchema<>(VoiceConfig.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<VoiceConfig>>asList(new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "enabled", VoiceConfig::enabled), new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "backend", VoiceConfig::backend), new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "deviceId", VoiceConfig::deviceId), new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "maxClipSeconds", VoiceConfig::maxClipSeconds), new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "language", VoiceConfig::language), new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "cpuThreads", VoiceConfig::cpuThreads), new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "nativeModelDirectory", VoiceConfig::nativeModelDirectory), new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "httpBaseUrl", VoiceConfig::httpBaseUrl), new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "httpModel", VoiceConfig::httpModel), new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "credential", VoiceConfig::credential), new dev.openallay.value.ValueSchema.Component<>(VoiceConfig.class, "gameplayAction", VoiceConfig::gameplayAction)), arguments -> new VoiceConfig((Boolean) arguments[0], (Backend) arguments[1], (String) arguments[2], (Integer) arguments[3], (String) arguments[4], (Integer) arguments[5], (String) arguments[6], (URI) arguments[7], (String) arguments[8], (CredentialReference) arguments[9], (GameplayAction) arguments[10]));
        }
    }
}
