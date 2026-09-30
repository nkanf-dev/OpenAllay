package dev.openallay.model.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelTurn;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OpenAiJsonCodecTest {
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
