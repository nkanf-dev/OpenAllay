package dev.openallay.model.anthropic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
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
    private final AnthropicJsonCodec codec = new AnthropicJsonCodec(dev.openallay.json.EngineJson.create());

    @Test
    void normalizesCacheReadsAndWritesIntoTotalInputAndKeepsPresence() {
        var usage = codec.parseUsage(dev.openallay.json.JsonTrees.parse("""
                {"input_tokens":100,"output_tokens":5,"cache_read_input_tokens":50,"cache_creation_input_tokens":25}
                """).getAsJsonObject());
        assertEquals(175, usage.inputTokens());
        assertEquals(100, usage.uncachedInputTokens());
        assertEquals(50, usage.cacheReadTokens());
        assertEquals(25, usage.cacheWriteTokens());
        assertTrue(usage.complete());
        var missing = codec.parseUsage(dev.openallay.json.JsonTrees.parse("""
                {"input_tokens":100,"output_tokens":5}
                """).getAsJsonObject());
        assertFalse(missing.inputKnown());
        assertTrue(missing.uncachedInputKnown());
        assertFalse(missing.cacheReadKnown());
        assertFalse(missing.cacheWriteKnown());
        assertFalse(codec.parseUsage(null).reported());
    }

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
            JsonObject encoded = dev.openallay.json.JsonTrees.parse(body).getAsJsonObject();
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

    @Test
    void sendsEffortInOutputConfigWithoutEnablingThinkingForBothRequestModes() {
        for (boolean stream : List.of(false, true)) {
            ModelRequest request = new ModelRequest("Use tools.",
                    List.of(ModelMessage.userText("question")), List.of(), stream);
            for (var effort : dev.openallay.model.config.ModelReasoningEffort.choices(
                    ModelProtocol.ANTHROPIC_MESSAGES)) {
                ModelConfig source = config();
                ModelConfig selected = new ModelConfig(source.enabled(), source.protocol(),
                        source.baseUri(), source.model(), source.apiKey(), source.contextWindowTokens(),
                        source.maxOutputTokens(), source.connectTimeout(), source.requestTimeout(), effort);
                JsonObject body = dev.openallay.json.JsonTrees.parse(codec.requestBody(selected, request))
                        .getAsJsonObject();
                assertEquals(effort != dev.openallay.model.config.ModelReasoningEffort.AUTO,
                        body.has("output_config"));
                if (body.has("output_config")) {
                    assertEquals(effort.encoded(), body.getAsJsonObject("output_config")
                            .get("effort").getAsString());
                }
                assertEquals(stream, body.get("stream").getAsBoolean());
                assertFalse(body.has("reasoning_effort"));
                assertFalse(body.has("thinking"));
                assertFalse(body.has("budget_tokens"));
            }
        }
    }

    private static void appendExchange(List<ModelMessage> messages, String id) {
        JsonObject input = new JsonObject();
        input.addProperty("durableProjection", true);
        messages.add(new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.ToolUse(id, "fact", input))));
        messages.add(new ModelMessage(ModelRole.USER, List.of(
                new ModelContent.ToolResult(id, dev.openallay.json.JsonTrees.parse("{\"status\":\"SUCCEEDED\"}"), false))));
    }

    private static ModelConfig config() {
        return new ModelConfig(true, ModelProtocol.ANTHROPIC_MESSAGES,
                URI.create("https://example.invalid/v1/"), "compatible-model", SecretValue.of("test-secret"),
                128_000, 1024, Duration.ofSeconds(5), Duration.ofSeconds(10));
    }
}
