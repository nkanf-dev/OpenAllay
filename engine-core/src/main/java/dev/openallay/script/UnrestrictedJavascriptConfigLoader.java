package dev.openallay.script;

import com.google.gson.JsonObject;
import dev.openallay.tool.ToolResult;
import java.io.Reader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Strict fail-closed loader for the local dangerous capability toggle. */
public final class UnrestrictedJavascriptConfigLoader {
    private static final Set<String> FIELDS = dev.openallay.util.Java8Collections.setOf("enabled");
    public ToolResult<UnrestrictedJavascriptConfig> load(Path path) {
        if (!Files.exists(path)) return new ToolResult.Success<>(UnrestrictedJavascriptConfig.defaults());
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { return load(reader); }
        catch (IOException e) { return failure("javascript_settings_read_failed"); }
    }
    public ToolResult<UnrestrictedJavascriptConfig> load(Reader reader) {
        try {
            JsonObject value = dev.openallay.json.JsonTrees.parse(reader).getAsJsonObject();
            if (!dev.openallay.json.JsonTrees.keys(value).equals(FIELDS)
                    || !value.get("enabled").isJsonPrimitive()
                    || !value.get("enabled").getAsJsonPrimitive().isBoolean()) return failure("invalid_javascript_settings");
            return new ToolResult.Success<>(new UnrestrictedJavascriptConfig(value.get("enabled").getAsBoolean()));
        } catch (RuntimeException e) { return failure("invalid_javascript_settings"); }
    }
    private static ToolResult.Failure<UnrestrictedJavascriptConfig> failure(String code) {
        return new ToolResult.Failure<>(code, "Unrestricted JavaScript settings are invalid or unavailable");
    }
}
