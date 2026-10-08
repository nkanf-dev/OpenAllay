package dev.openallay.script.command;

import dev.openallay.settings.AtomicSettingsFile;
import dev.openallay.settings.SettingsWriteException;
import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Objects;

/** Atomically persists one validated experimental-command setting. */
public final class CommandCapabilityConfigStore {
    private final Path path;
    private final AtomicSettingsFile files;
    private final CommandCapabilityConfigLoader loader = new CommandCapabilityConfigLoader();
    private final CommandCapabilityConfigWriter writer = new CommandCapabilityConfigWriter();
    private volatile CommandCapabilityConfig current = CommandCapabilityConfig.defaults();

    public CommandCapabilityConfigStore(Path path) {
        this(path, new AtomicSettingsFile());
    }

    CommandCapabilityConfigStore(Path path, AtomicSettingsFile files) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.files = Objects.requireNonNull(files, "files");
    }

    public CommandCapabilityConfig current() {
        return current;
    }

    public synchronized ToolResult<CommandCapabilityConfig> reload() {
        ToolResult<CommandCapabilityConfig> loaded = loader.load(path);
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.script.command.CommandCapabilityConfig> value; ToolResult.Success<CommandCapabilityConfig> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<CommandCapabilityConfig>) $oaPattern0_holder.value) != null))) {
            current = $oaPattern0_holder.bound.value();
        }
        return loaded;
    }

    public synchronized ToolResult<CommandCapabilityConfig> save(
            CommandCapabilityConfig candidate) {
        Objects.requireNonNull(candidate, "candidate");
        String encoded;
        CommandCapabilityConfig validated;
        try {
            encoded = writer.encode(candidate);
            ToolResult<CommandCapabilityConfig> decoded =
                    loader.load(new StringReader(encoded));
            final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.script.command.CommandCapabilityConfig> value; ToolResult.Failure<CommandCapabilityConfig> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = decoded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<CommandCapabilityConfig>) $oaPattern1_holder.value) != null))) {
                return $oaPattern1_holder.bound;
            }
            validated =
                    ((ToolResult.Success<CommandCapabilityConfig>) decoded).value();
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "invalid_command_settings",
                    "Unable to prepare command settings");
        }
        try {
            files.replace(path, encoded);
        } catch (SettingsWriteException failure) {
            return new ToolResult.Failure<>(failure.code(), failure.getMessage());
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "settings_write_failed", "Unable to save command settings");
        }
        current = validated;
        return new ToolResult.Success<>(validated);
    }
}
