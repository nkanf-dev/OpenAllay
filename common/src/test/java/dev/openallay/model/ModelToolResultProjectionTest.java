package dev.openallay.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.model.anthropic.AnthropicJsonCodec;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.openai.OpenAiJsonCodec;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelToolResultProjectionTest {
    @Test
    void genericCanonicalResultBecomesCliTextWithoutLosingFields() {
        JsonObject normalized = JsonParser.parseString("""
                {
                  "status": "success",
                  "outputType": "example.Skill",
                  "value": {
                    "name": "analyze-game-data",
                    "instructions": "filter first\\nreturn a compact result",
                    "allowedTools": ["openallay:run_javascript"]
                  }
                }
                """).getAsJsonObject();

        String text = new AgentToolResult(
                        "openallay:load_skill", normalized, false)
                .modelValue()
                .getAsString();

        assertEquals("""
                status: success
                result:
                  name: analyze-game-data
                  instructions:
                    filter first
                    return a compact result
                  allowedTools:
                    - openallay:run_javascript""", text);
        assertFalse(text.contains("{"));
        assertFalse(text.contains("\""));
    }

    @Test
    void providerReceivesNaturalTextProjectionWithoutJsonStringQuoting() {
        ModelMessage result = new ModelMessage(
                ModelRole.USER,
                List.of(new ModelContent.ToolResult(
                        "call-1",
                        new JsonPrimitive("result: r_1\nid: example:sword"),
                        false)));
        ModelRequest request =
                new ModelRequest("system", List.of(result), List.of(), false);

        var openai = JsonParser.parseString(
                        new OpenAiJsonCodec(new Gson()).requestBody(config(), request))
                .getAsJsonObject();
        String openaiContent = openai.getAsJsonArray("messages")
                .get(1).getAsJsonObject().get("content").getAsString();
        assertEquals("result: r_1\nid: example:sword", openaiContent);
        assertFalse(openaiContent.startsWith("\""));

        var anthropic = JsonParser.parseString(
                        new AnthropicJsonCodec(new Gson()).requestBody(config(), request))
                .getAsJsonObject();
        String anthropicContent = anthropic.getAsJsonArray("messages")
                .get(0).getAsJsonObject().getAsJsonArray("content")
                .get(0).getAsJsonObject().get("content").getAsString();
        assertEquals("result: r_1\nid: example:sword", anthropicContent);
        assertFalse(anthropicContent.startsWith("\""));
    }

    private static ModelConfig config() {
        return new ModelConfig(
                true,
                ModelProtocol.OPENAI_CHAT,
                URI.create("https://example.com/v1/"),
                "test",
                SecretValue.of("test"),
                100_000,
                4_096,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10));
    }
}
