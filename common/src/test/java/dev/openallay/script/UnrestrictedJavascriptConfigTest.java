package dev.openallay.script;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import dev.openallay.tool.ToolResult;

final class UnrestrictedJavascriptConfigTest {
 @Test void defaultsOffAndStrictlyRejectsUnknownFields() {
   assertFalse(UnrestrictedJavascriptConfig.defaults().enabled());
   var loader = new UnrestrictedJavascriptConfigLoader();
   assertInstanceOf(ToolResult.Failure.class, loader.load(new StringReader("{\"schemaVersion\":1,\"enabled\":true,\"extra\":0}")));
 }
}
