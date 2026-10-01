package dev.openallay.model.anthropic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AnthropicJsonCodecTest {
    private final AnthropicJsonCodec codec = new AnthropicJsonCodec(new Gson());

    @Test
    void mapsQualifiedHistoryAndResultPairsWithoutChangingDurableIds() {
        String providerId = "toolu_" + "x".repeat(24);
        String restoredId = "00000000-0000-0000-0000-000000000001:" + providerId;
        String otherRestoredId = "00000000-0000-0000-0000-000000000002:" + providerId;
        String longProviderId = "toolu_" + "y".repeat(100);
        for (boolean stream : List.of(false, true)) {
            List<ModelMessage> messages = new ArrayList<>();
            messages.add(ModelMessage.userText("earlier request"));
            appendExchange(messages, restoredId);
            appendExchange(messages, otherRestoredId);
            appendExchange(messages, providerId);
            appendExchange(messages, longProviderId);
            messages.add(ModelMessage.userText("next request"));
            ModelRequest request = new ModelRequest("Use tools.", messages, List.of(), stream);
            List<ModelMessage> original = List.copyOf(messages);

            String body = codec.requestBody(config(), request);
            JsonObject encoded = JsonParser.parseString(body).getAsJsonObject();
            List<String> uses = new ArrayList<>();
            List<String> results = new ArrayList<>();
            for (var message : encoded.getAsJsonArray("messages")) {
                for (var content : message.getAsJsonObject().getAsJsonArray("content")) {
                    JsonObject block = content.getAsJsonObject();
                    if (block.get("type").getAsString().equals("tool_use")) {
                        String id = block.get("id").getAsString();
                        assertTrue(id.matches("[a-zA-Z0-9_-]+"));
                        uses.add(id);
                    } else if (block.get("type").getAsString().equals("tool_result")) {
                        String id = block.get("tool_use_id").getAsString();
                        assertTrue(id.matches("[a-zA-Z0-9_-]+"));
                        results.add(id);
                    }
                }
            }
            assertEquals(4, uses.stream().distinct().count());
            assertEquals(uses, results);
            assertNotEquals(uses.get(0), uses.get(1));
            assertEquals(providerId, uses.get(2));
            // OpenAI's 64-character field limit is not an Anthropic product limit.
            assertEquals(longProviderId, uses.get(3));
            assertFalse(body.contains(restoredId));
            assertFalse(body.contains(otherRestoredId));
            assertEquals(stream, encoded.get("stream").getAsBoolean());
            assertEquals(body, codec.requestBody(config(), request));
            assertEquals(original, request.messages());
            assertEquals(restoredId,
                    ((ModelContent.ToolUse) request.messages().get(1).content().getFirst()).id());
            assertEquals(restoredId,
                    ((ModelContent.ToolResult) request.messages().get(2).content().getFirst()).toolUseId());
        }
    }

    private static void appendExchange(List<ModelMessage> messages, String id) {
        JsonObject input = new JsonObject();
        input.addProperty("durableProjection", true);
        messages.add(new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.ToolUse(id, "fact", input))));
        messages.add(new ModelMessage(ModelRole.USER, List.of(
                new ModelContent.ToolResult(id, JsonParser.parseString("{\"status\":\"SUCCEEDED\"}"), false))));
    }

    private static ModelConfig config() {
        return new ModelConfig(true, ModelProtocol.ANTHROPIC_MESSAGES,
                URI.create("https://example.invalid/v1/"), "compatible-model", SecretValue.of("test-secret"),
                128_000, 1024, Duration.ofSeconds(5), Duration.ofSeconds(10));
    }
}
