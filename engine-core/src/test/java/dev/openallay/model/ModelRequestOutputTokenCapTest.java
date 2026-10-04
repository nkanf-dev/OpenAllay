package dev.openallay.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.model.anthropic.AnthropicJsonCodec;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.openai.OpenAiJsonCodec;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelRequestOutputTokenCapTest {
    @Test
    void convenienceConstructorsKeepConfiguredLimitForBothRequestModesAndCodecs() {
        for (boolean stream : List.of(false, true)) {
            ModelRequest defaultSession = new ModelRequest(
                    "Use tools.", List.of(ModelMessage.userText("question")), List.of(), stream);
            ModelRequest namedSession = new ModelRequest(
                    "Use tools.", List.of(ModelMessage.userText("question")), List.of(), stream, "named-session");
            assertEquals("default", defaultSession.sessionKey());
            assertEquals("named-session", namedSession.sessionKey());
            for (ModelRequest request : List.of(defaultSession, namedSession)) {
                assertNull(request.maxOutputTokens());
                assertEquals(1024, request.effectiveMaxOutputTokens(1024));
                assertEncodedLimit(request, 1024);
            }
        }
    }

    @Test
    void explicitCapsOnlyReduceConfiguredLimitForBothRequestModesAndCodecs() {
        for (boolean stream : List.of(false, true)) {
            for (int cap : List.of(1, 37, 1024, 1025, Integer.MAX_VALUE)) {
                ModelRequest request = new ModelRequest(
                        "Use tools.", List.of(ModelMessage.userText("question")), List.of(), stream,
                        "named-session", cap);
                int expected = Math.min(1024, cap);
                assertEquals(expected, request.effectiveMaxOutputTokens(1024));
                assertEncodedLimit(request, expected);
                assertEquals(Integer.valueOf(cap), request.maxOutputTokens());
            }
        }
    }

    @Test
    void rejectsNonpositiveRequestCaps() {
        for (int cap : List.of(0, -1, Integer.MIN_VALUE)) {
            assertThrows(IllegalArgumentException.class, () -> new ModelRequest(
                    "Use tools.", List.of(ModelMessage.userText("question")), List.of(), false,
                    "named-session", cap));
        }
    }

    @Test
    void actualPlayerContentAndOptionalCapSurviveStructuralContextRoundTrips() {
        String playerValue = "password=player-value token=player-token";
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("description", playerValue);
        ModelToolDefinition tool = new ModelToolDefinition("fact", playerValue, schema);
        ModelContextCodec contexts = new ModelContextCodec();
        for (boolean stream : List.of(false, true)) {
            for (Integer cap : new Integer[] {null, 37}) {
                ModelRequest request = new ModelRequest(
                        "Use tools. " + playerValue, List.of(ModelMessage.userText("question " + playerValue)),
                        List.of(tool), stream, "session-" + playerValue, cap);
                ModelRequest restored = new ModelRequest(request.systemPrompt(),
                        contexts.decode(contexts.encode(request.messages())), request.tools(),
                        request.stream(), request.sessionKey(), request.maxOutputTokens());

                assertEquals(request, restored);
                assertEquals(cap, restored.maxOutputTokens());
                assertEquals(stream, restored.stream());
                assertEquals("Use tools. " + playerValue, restored.systemPrompt());
                assertEquals(playerValue, schema.get("description").getAsString());
                assertEquals(new Gson().toJsonTree(request), new Gson().toJsonTree(restored));
                assertFalse(new Gson().toJson(restored).contains("[REDACTED]"));
                assertEncodedLimit(restored, cap == null ? 1024 : cap);
            }
        }
    }

    private static void assertEncodedLimit(ModelRequest request, int expected) {
        for (ModelProtocol protocol : List.of(ModelProtocol.OPENAI_CHAT, ModelProtocol.ANTHROPIC_MESSAGES)) {
            ModelConfig config = config(protocol);
            String body = protocol == ModelProtocol.OPENAI_CHAT
                    ? new OpenAiJsonCodec(new Gson()).requestBody(config, request)
                    : new AnthropicJsonCodec(new Gson()).requestBody(config, request);
            JsonObject encoded = JsonParser.parseString(body).getAsJsonObject();
            String field = protocol == ModelProtocol.OPENAI_CHAT ? "max_completion_tokens" : "max_tokens";
            assertEquals(expected, encoded.get(field).getAsInt());
            assertEquals(request.stream(), encoded.get("stream").getAsBoolean());
            assertEquals(1024, config.maxOutputTokens());
        }
    }

    private static ModelConfig config(ModelProtocol protocol) {
        return new ModelConfig(true, protocol, URI.create("https://example.invalid/v1/"),
                "compatible-model", SecretValue.of("test-secret"), 128_000, 1024,
                Duration.ofSeconds(5), Duration.ofSeconds(10));
    }
}
