package dev.openallay.script.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CommandCapabilityConfigTest {
    @TempDir Path temporary;

    @Test
    void missingConfigDefaultsOffAndSaveRoundTripsStrictly() {
        CommandCapabilityConfigStore store =
                new CommandCapabilityConfigStore(temporary.resolve("commands.json"));
        ToolResult<CommandCapabilityConfig> loaded = store.reload();
        assertFalse(((ToolResult.Success<CommandCapabilityConfig>) loaded).value().enabled());

        ToolResult<CommandCapabilityConfig> saved = store.save(
                new CommandCapabilityConfig(true));
        assertTrue(((ToolResult.Success<CommandCapabilityConfig>) saved).value().enabled());

        CommandCapabilityConfigStore restarted =
                new CommandCapabilityConfigStore(temporary.resolve("commands.json"));
        assertTrue(((ToolResult.Success<CommandCapabilityConfig>) restarted.reload())
                .value().enabled());
    }

    @Test
    void unknownFieldsAndWrongTypesFailClosed() {
        CommandCapabilityConfigLoader loader = new CommandCapabilityConfigLoader();
        assertInstanceOf(
                ToolResult.Failure.class,
                loader.load(new StringReader("""
                        {"enabled":true,"surprise":true}
                        """)));
        assertInstanceOf(
                ToolResult.Failure.class,
                loader.load(new StringReader("""
                        {"enabled":"true"}
                        """)));
    }
}
