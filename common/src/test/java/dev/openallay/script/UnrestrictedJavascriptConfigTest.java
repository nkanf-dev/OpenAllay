package dev.openallay.script;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class UnrestrictedJavascriptConfigTest {
    @Test
    void defaultsOffAndWriterRoundTripsOnlyEnabled() {
        assertFalse(UnrestrictedJavascriptConfig.defaults().enabled());
        var config = new UnrestrictedJavascriptConfig(true);
        String encoded = new UnrestrictedJavascriptConfigWriter().encode(config);

        assertEquals("{\n  \"enabled\": true\n}\n", encoded);
        var result = new UnrestrictedJavascriptConfigLoader().load(new StringReader(encoded));
        assertEquals(config, ((ToolResult.Success<UnrestrictedJavascriptConfig>) result).value());
    }

    @Test
    void invalidSettingsFailClosedWithoutRewriting(@TempDir Path directory) throws Exception {
        var loader = new UnrestrictedJavascriptConfigLoader();
        Path path = directory.resolve("unrestricted-javascript.json");
        for (String invalid : new String[] {
                "{}", "{\"enabled\":true,\"extra\":0}",
                "{\"enabled\":\"true\"}", "{\"enabled\":1}", "{\"enabled\":null}"
        }) {
            Files.writeString(path, invalid);
            var result = loader.load(path);
            assertInstanceOf(ToolResult.Failure.class, result);
            assertEquals(invalid, Files.readString(path));
        }
    }
}
