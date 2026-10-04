package dev.openallay.model.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.http.SseEvent;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class OpenAiJsonCodecTest {
    @Test
    void absentNullAndPartialUsageStayUnknownWhileExplicitZeroIsReported() {
        for (String raw : List.of("null", "{}", "{\"prompt_tokens\":3}",
                "{\"prompt_tokens\":null,\"completion_tokens\":1}",
                "{\"prompt_tokens\":0,\"completion_tokens\":0}",
                "{\"prompt_tokens\":9,\"completion_tokens\":2,\"extra_provider_field\":true}")) {
            JsonObject response = textResponse();
            response.add("usage", JsonParser.parseString(raw));
            List<ModelEvent> events = new ArrayList<>();
            codec.parseTurn(response.toString(), events::add);
            assertEquals(!raw.equals("null"), events.stream().anyMatch(ModelEvent.UsageUpdate.class::isInstance));
        }
    }

    private final OpenAiJsonCodec codec = new OpenAiJsonCodec(new Gson());

    @Test
    void acceptsMissingAndNullOptionalToolCallsAndUsage() {
        for (boolean nullToolCalls : List.of(false, true)) {
            for (boolean nullUsage : List.of(false, true)) {
                JsonObject response = textResponse();
                if (nullToolCalls) {
                    message(response).add("tool_calls", JsonNull.INSTANCE);
                }
                if (nullUsage) {
                    response.add("usage", JsonNull.INSTANCE);
                }
                List<ModelEvent> events = new ArrayList<>();

                ModelTurn turn = codec.parseTurn(response.toString(), events::add);

                assertEquals("OK", turn.text());
                assertTrue(turn.toolUses().isEmpty());
                assertEquals(0, turn.usage().inputTokens());
                assertEquals(0, turn.usage().outputTokens());
                assertEquals(0, turn.usage().cacheReadTokens());
                assertTrue(events.stream().anyMatch(ModelEvent.MessageComplete.class::isInstance));
                assertTrue(events.stream().noneMatch(ModelEvent.UsageUpdate.class::isInstance));
            }
        }
    }

    @Test
    void acceptsMissingAndNullOptionalPromptTokenDetails() {
        for (String details : List.of("missing", "null", "{}", "{\"cached_tokens\":null}")) {
            JsonObject response = textResponse();
            JsonObject usage = JsonParser.parseString(
                    "{\"prompt_tokens\":7,\"completion_tokens\":2}").getAsJsonObject();
            if (!details.equals("missing")) {
                usage.add("prompt_tokens_details", JsonParser.parseString(details));
            }
            response.add("usage", usage);

            ModelTurn turn = codec.parseTurn(response.toString(), ignored -> {});

            assertEquals("OK", turn.text());
            assertEquals(7, turn.usage().inputTokens());
            assertEquals(2, turn.usage().outputTokens());
            assertEquals(0, turn.usage().cacheReadTokens());
            assertTrue(turn.usage().inputKnown());
            assertTrue(!turn.usage().cacheReadKnown());
            assertTrue(!turn.usage().uncachedInputKnown());
        }
    }

    @Test
    void rejectsMalformedNonNullOptionalFieldsRatherThanDroppingThem() {
        JsonObject toolCalls = textResponse();
        message(toolCalls).add("tool_calls", new JsonObject());
        assertThrows(RuntimeException.class,
                () -> codec.parseTurn(toolCalls.toString(), ignored -> {}));

        JsonObject usage = textResponse();
        usage.add("usage", JsonParser.parseString("[]"));
        assertThrows(RuntimeException.class,
                () -> codec.parseTurn(usage.toString(), ignored -> {}));

        JsonObject details = textResponse();
        JsonObject usageObject = new JsonObject();
        usageObject.add("prompt_tokens_details", JsonParser.parseString("[]"));
        details.add("usage", usageObject);
        assertThrows(RuntimeException.class,
                () -> codec.parseTurn(details.toString(), ignored -> {}));

        JsonObject arguments = textResponse();
        message(arguments).add("tool_calls", JsonParser.parseString("""
                [{"id":"call_x","type":"function","function":{"name":"fact","arguments":"[]"}}]
                """));
        assertThrows(RuntimeException.class,
                () -> codec.parseTurn(arguments.toString(), ignored -> {}));
    }

    @Test
    void restoredQualifiedHistoryFitsStrictWireSchemaForCompleteAndStreamContinuation() {
        String providerId = "call_" + "x".repeat(24);
        String restoredId = "00000000-0000-0000-0000-000000000001:" + providerId;
        String otherRestoredId = "00000000-0000-0000-0000-000000000002:" + providerId;
        assertEquals(29, providerId.length());
        assertEquals(66, restoredId.length());
        // The old verbatim wire encoding fails the exact upstream schema boundary.
        assertThrows(IllegalArgumentException.class, () -> requireProviderSafeId(restoredId));

        for (boolean stream : List.of(false, true)) {
            ModelTurn current = currentToolTurn(providerId, stream);
            assertEquals(providerId, current.toolUses().getFirst().id());
            List<ModelMessage> messages = new ArrayList<>();
            messages.add(ModelMessage.userText("earlier request"));
            appendExchange(messages, restoredId);
            appendExchange(messages, otherRestoredId);
            messages.add(ModelMessage.userText("current request"));
            messages.add(new ModelMessage(ModelRole.ASSISTANT, current.content()));
            messages.add(new ModelMessage(ModelRole.USER, List.of(
                    new ModelContent.ToolResult(providerId, JsonParser.parseString("{\"fact\":42}"), false))));
            messages.add(ModelMessage.userText("next request"));
            ModelRequest request = new ModelRequest("Use tools.", messages, List.of(), stream);
            List<ModelMessage> original = List.copyOf(messages);

            String body = codec.requestBody(config(), request);
            JsonObject encoded = JsonParser.parseString(body).getAsJsonObject();
            Set<String> uses = new HashSet<>();
            Set<String> results = new HashSet<>();
            List<String> orderedUses = new ArrayList<>();
            List<String> orderedResults = new ArrayList<>();
            for (var messageElement : encoded.getAsJsonArray("messages")) {
                JsonObject message = messageElement.getAsJsonObject();
                if (message.has("tool_calls")) {
                    for (var callElement : message.getAsJsonArray("tool_calls")) {
                        String id = callElement.getAsJsonObject().get("id").getAsString();
                        requireProviderSafeId(id);
                        assertTrue(uses.add(id), "wire tool-call IDs must be unique");
                        orderedUses.add(id);
                    }
                }
                if (message.has("tool_call_id")) {
                    String id = message.get("tool_call_id").getAsString();
                    requireProviderSafeId(id);
                    assertTrue(results.add(id), "wire result IDs must be unique");
                    orderedResults.add(id);
                }
            }
            assertEquals(3, uses.size());
            assertEquals(uses, results);
            assertEquals(orderedUses, orderedResults);
            assertEquals(providerId, orderedUses.getLast());
            assertNotEquals(orderedUses.get(0), orderedUses.get(1));
            assertFalse(body.contains(restoredId));
            assertFalse(body.contains(otherRestoredId));
            assertEquals(stream, encoded.get("stream").getAsBoolean());
            assertEquals(body, codec.requestBody(config(), request));
            assertEquals(original, request.messages());
            assertEquals(restoredId,
                    ((ModelContent.ToolUse) request.messages().get(1).content().getFirst()).id());
            assertEquals(restoredId,
                    ((ModelContent.ToolResult) request.messages().get(2).content().getFirst()).toolUseId());
            // Re-encoding a subsequent dispatch still preserves the same history aliases.
            messages.add(ModelMessage.userText("one more request"));
            JsonObject subsequent = JsonParser.parseString(codec.requestBody(config(),
                    new ModelRequest("Use tools.", messages, List.of(), stream))).getAsJsonObject();
            assertEquals(orderedUses.getFirst(), subsequent.getAsJsonArray("messages").get(2)
                    .getAsJsonObject().getAsJsonArray("tool_calls").get(0)
                    .getAsJsonObject().get("id").getAsString());
        }
    }

    @Test
    void sendsEveryExplicitEffortAndOmitsAutoForBothCompleteAndStreamRequests() {
        for (boolean stream : List.of(false, true)) {
            ModelRequest request = new ModelRequest("Use tools.",
                    List.of(ModelMessage.userText("question")), List.of(), stream);
            for (var effort : dev.openallay.model.config.ModelReasoningEffort.values()) {
                ModelConfig source = config();
                ModelConfig selected = new ModelConfig(source.enabled(), source.protocol(),
                        source.baseUri(), source.model(), source.apiKey(), source.contextWindowTokens(),
                        source.maxOutputTokens(), source.connectTimeout(), source.requestTimeout(), effort);
                JsonObject body = JsonParser.parseString(codec.requestBody(selected, request))
                        .getAsJsonObject();
                assertEquals(effort != dev.openallay.model.config.ModelReasoningEffort.AUTO,
                        body.has("reasoning_effort"));
                if (body.has("reasoning_effort")) {
                    assertEquals(effort.encoded(), body.get("reasoning_effort").getAsString());
                }
                assertEquals(stream, body.get("stream").getAsBoolean());
                assertFalse(body.has("output_config"));
                assertFalse(body.has("thinking"));
                assertEquals(request.messages(), List.of(ModelMessage.userText("question")));
            }
        }
    }

    private ModelTurn currentToolTurn(String id, boolean stream) {
        if (!stream) {
            JsonObject response = textResponse();
            message(response).add("tool_calls", JsonParser.parseString("""
                    [{"id":"%s","type":"function","function":{"name":"fact","arguments":"{}"}}]
                    """.formatted(id)));
            response.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .addProperty("finish_reason", "tool_calls");
            return codec.parseTurn(response.toString(), ignored -> {});
        }
        OpenAiStreamAccumulator accumulator = new OpenAiStreamAccumulator(ignored -> {});
        accumulator.accept(new SseEvent(null, """
                {"model":"compatible-model","choices":[{"finish_reason":"tool_calls","delta":{
                 "tool_calls":[{"index":0,"id":"%s","type":"function",
                  "function":{"name":"fact","arguments":"{}"}}]}}]}
                """.formatted(id)));
        return accumulator.finish();
    }

    private static void appendExchange(List<ModelMessage> messages, String id) {
        JsonObject input = new JsonObject();
        input.addProperty("durableProjection", true);
        messages.add(new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.ToolUse(id, "fact", input))));
        messages.add(new ModelMessage(ModelRole.USER, List.of(
                new ModelContent.ToolResult(id, JsonParser.parseString("{\"status\":\"SUCCEEDED\"}"), false))));
    }

    private static void requireProviderSafeId(String id) {
        if (id.length() > 64 || !id.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid wire tool-call ID");
        }
    }

    private static ModelConfig config() {
        return new ModelConfig(true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://example.invalid/v1/"), "compatible-model", SecretValue.of("test-secret"),
                128_000, 1024, Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    private static JsonObject textResponse() {
        return JsonParser.parseString("""
                {"model":"compatible-model","choices":[{"finish_reason":"stop",
                 "message":{"role":"assistant","content":"OK"}}]}
                """).getAsJsonObject();
    }

    private static JsonObject message(JsonObject response) {
        return response.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
    }
}
