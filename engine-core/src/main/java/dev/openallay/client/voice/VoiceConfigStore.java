package dev.openallay.client.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import dev.openallay.model.config.CredentialReference;
import dev.openallay.settings.AtomicSettingsFile;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Exact current shape; malformed files are retained, never replaced with defaults. */
public final class VoiceConfigStore {
    private static final Set<String> FIELDS = dev.openallay.util.Java8Collections.setOf("enabled", "backend", "deviceId", "maxClipSeconds", "language", "cpuThreads", "nativeModelDirectory", "httpBaseUrl", "httpModel", "credentialRef", "gameplayAction");
    private final Path path;
    private volatile VoiceConfig config = VoiceConfig.defaults();
    public VoiceConfigStore(Path path) { this.path = path; }
    public VoiceConfig config() { return config; }
    public synchronized ToolResult<VoiceConfig> reload() {
        if (!Files.exists(path)) return new ToolResult.Success<>(config);
        try {
            if (Files.size(path) > 64 * 1024) throw new IllegalArgumentException();
            VoiceConfig next = decode(dev.openallay.util.Java8Files.readString(path));
            config = next;
            return new ToolResult.Success<>(next);
        } catch (Exception failure) { return new ToolResult.Failure<>("invalid_voice_config", "Voice settings are invalid; the last valid settings are retained"); }
    }
    public synchronized ToolResult<VoiceConfig> save(VoiceConfig candidate) {
        try {
            String contents = encode(candidate);
            if (!decode(contents).equals(candidate)) throw new IllegalArgumentException();
            new AtomicSettingsFile().replace(path, contents);
            config = candidate;
            return new ToolResult.Success<>(candidate);
        } catch (RuntimeException failure) { return new ToolResult.Failure<>("voice_save_failed", "Unable to save voice settings; the last valid settings are retained"); }
    }
    public static String encode(VoiceConfig c) {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", c.enabled()); o.addProperty("backend", c.backend().name());
        o.addProperty("gameplayAction", c.gameplayAction().name());
        o.addProperty("deviceId", c.deviceId()); o.addProperty("maxClipSeconds", c.maxClipSeconds());
        o.addProperty("language", c.language()); o.addProperty("cpuThreads", c.cpuThreads());
        o.addProperty("nativeModelDirectory", c.nativeModelDirectory()); o.addProperty("httpBaseUrl", c.httpBaseUrl().toString());
        o.addProperty("httpModel", c.httpModel());
        if (c.credential() == null) o.add("credentialRef", com.google.gson.JsonNull.INSTANCE);
        else o.addProperty("credentialRef", c.credential().encoded());
        return o.toString() + "\n";
    }
    public static VoiceConfig decode(String contents) {
        JsonElement parsed = dev.openallay.json.JsonTrees.parse(contents);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException();
        JsonObject o = parsed.getAsJsonObject();
        if (!dev.openallay.json.JsonTrees.keys(o).equals(FIELDS)) throw new IllegalArgumentException();
        JsonElement enabled = o.get("enabled");
        if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
        CredentialReference credential = o.get("credentialRef").isJsonNull() ? null : CredentialReference.parse(string(o, "credentialRef"));
        return new VoiceConfig(enabled.getAsBoolean(), VoiceConfig.Backend.valueOf(string(o, "backend")), string(o, "deviceId"),
                integer(o, "maxClipSeconds"), string(o, "language"), integer(o, "cpuThreads"), string(o, "nativeModelDirectory"),
                URI.create(string(o, "httpBaseUrl")), string(o, "httpModel"), credential, VoiceConfig.GameplayAction.valueOf(string(o, "gameplayAction")));
    }
    private static String string(JsonObject o, String field) {
        JsonElement e = o.get(field);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        return e.getAsString();
    }
    private static int integer(JsonObject o, String field) {
        JsonElement e = o.get(field);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        return e.getAsBigDecimal().intValueExact();
    }
}
