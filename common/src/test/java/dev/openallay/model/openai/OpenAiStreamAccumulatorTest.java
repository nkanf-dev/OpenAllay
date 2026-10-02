package dev.openallay.model.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.http.SseEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OpenAiStreamAccumulatorTest {
    @Test
    void absentNullAndPartialUsageStayUnknownWhileExplicitZeroIsReported() {
        for (String raw : List.of("null", "{}", "{\"prompt_tokens\":3}",
                "{\"prompt_tokens\":null,\"completion_tokens\":1}",
                "{\"prompt_tokens\":0,\"completion_tokens\":0}",
                "{\"prompt_tokens\":9,\"completion_tokens\":2,\"extra_provider_field\":true}")) {
            JsonObject response = textChunk();
            response.add("usage", JsonParser.parseString(raw));
            List<ModelEvent> events = new ArrayList<>();
            OpenAiStreamAccumulator accumulator = new OpenAiStreamAccumulator(events::add);
            accumulator.accept(event(response));
            accumulator.finish();
            assertEquals(!raw.equals("null"), events.stream().anyMatch(ModelEvent.UsageUpdate.class::isInstance));
        }
    }

    @Test
    void acceptsMissingAndNullOptionalToolCallsAndUsage() {
        for (boolean nullToolCalls : List.of(false, true)) {
            for (boolean nullUsage : List.of(false, true)) {
                List<ModelEvent> events = new ArrayList<>();
                OpenAiStreamAccumulator accumulator = new OpenAiStreamAccumulator(events::add);
                JsonObject response = textChunk();
                if (nullToolCalls) {
                    delta(response).add("tool_calls", JsonNull.INSTANCE);
                }
                if (nullUsage) {
                    response.add("usage", JsonNull.INSTANCE);
                }

                accumulator.accept(event(response));
                ModelTurn turn = accumulator.finish();

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
            OpenAiStreamAccumulator accumulator = new OpenAiStreamAccumulator(ignored -> {});
            JsonObject response = textChunk();
            JsonObject usage = JsonParser.parseString(
                    "{\"prompt_tokens\":7,\"completion_tokens\":2}").getAsJsonObject();
            if (!details.equals("missing")) {
                usage.add("prompt_tokens_details", JsonParser.parseString(details));
            }
            response.add("usage", usage);

            accumulator.accept(event(response));
            ModelTurn turn = accumulator.finish();

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
        JsonObject toolCalls = textChunk();
        delta(toolCalls).add("tool_calls", new JsonObject());
        assertThrows(RuntimeException.class,
                () -> new OpenAiStreamAccumulator(ignored -> {}).accept(event(toolCalls)));

        JsonObject usage = textChunk();
        usage.add("usage", JsonParser.parseString("[]"));
        assertThrows(RuntimeException.class,
                () -> new OpenAiStreamAccumulator(ignored -> {}).accept(event(usage)));

        JsonObject details = textChunk();
        JsonObject usageObject = new JsonObject();
        usageObject.add("prompt_tokens_details", JsonParser.parseString("[]"));
        details.add("usage", usageObject);
        assertThrows(RuntimeException.class,
                () -> new OpenAiStreamAccumulator(ignored -> {}).accept(event(details)));

        JsonObject arguments = textChunk();
        delta(arguments).add("tool_calls", JsonParser.parseString("""
                [{"index":0,"id":"call_x","type":"function",
                  "function":{"name":"fact","arguments":"[]"}}]
                """));
        OpenAiStreamAccumulator accumulator = new OpenAiStreamAccumulator(ignored -> {});
        accumulator.accept(event(arguments));
        assertThrows(RuntimeException.class, accumulator::finish);
    }

    private static JsonObject textChunk() {
        return JsonParser.parseString("""
                {"model":"compatible-model","choices":[{"finish_reason":"stop",
                 "delta":{"role":"assistant","content":"OK"}}]}
                """).getAsJsonObject();
    }

    private static JsonObject delta(JsonObject response) {
        return response.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("delta");
    }

    private static SseEvent event(JsonObject response) {
        return new SseEvent(null, response.toString());
    }
}
