package dev.openallay.script.command;

import com.google.gson.JsonObject;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Strict, pre-release loader. Missing files use defaults; malformed files fail closed. */
public final class CommandCapabilityConfigLoader {
    private static final Set<String> FIELDS = dev.openallay.util.Java8Collections.setOf("enabled");

    public ToolResult<CommandCapabilityConfig> load(Path path) {
        if (!Files.exists(path)) {
            return new ToolResult.Success<>(CommandCapabilityConfig.defaults());
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (IOException failure) {
            return failure("command_settings_read_failed", "Unable to read command settings");
        }
    }

    public ToolResult<CommandCapabilityConfig> load(Reader reader) {
        try {
            JsonObject value = dev.openallay.json.JsonTrees.parse(reader).getAsJsonObject();
            if (!dev.openallay.json.JsonTrees.keys(value).equals(FIELDS)
                    || !value.get("enabled").isJsonPrimitive()
                    || !value.get("enabled").getAsJsonPrimitive().isBoolean()) {
                return failure(
                        "invalid_command_settings",
                        "Command settings must contain only a boolean enabled field");
            }
            boolean enabled = value.get("enabled").getAsBoolean();
            return new ToolResult.Success<>(
                    new CommandCapabilityConfig(enabled));
        } catch (RuntimeException failure) {
            return failure("invalid_command_settings", "Command settings are invalid");
        }
    }

    private static ToolResult.Failure<CommandCapabilityConfig> failure(
            String code, String message) {
        return new ToolResult.Failure<>(code, message);
    }
}
