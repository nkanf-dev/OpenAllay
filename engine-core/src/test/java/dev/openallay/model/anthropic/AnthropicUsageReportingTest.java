package dev.openallay.model.anthropic;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.http.SseEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AnthropicUsageReportingTest {
    @Test
    void jsonPreservesPartialReportsAndExplicitZeroWithoutInventingMissingCounters() {
        for (String raw : List.of("missing", "null", "{}", "{\"input_tokens\":3}",
                "{\"input_tokens\":null,\"output_tokens\":1}",
                "{\"input_tokens\":0,\"output_tokens\":0}",
                "{\"input_tokens\":3,\"output_tokens\":1,\"extra_field\":42}")) {
            JsonObject response = JsonParser.parseString("""
                    {"model":"claude-test","stop_reason":"end_turn","content":[{"type":"text","text":"OK"}]}
                    """).getAsJsonObject();
            if (!raw.equals("missing")) response.add("usage", JsonParser.parseString(raw));
            List<ModelEvent> events = new ArrayList<>();
            var turn = new AnthropicJsonCodec(new Gson()).parseTurn(response.toString(), events::add);
            boolean hasUsageObject = !raw.equals("missing") && !raw.equals("null");
            JsonObject reported = hasUsageObject ? JsonParser.parseString(raw).getAsJsonObject() : null;
            boolean hasInput = AnthropicJsonCodec.hasCount(reported, "input_tokens");
            boolean hasOutput = AnthropicJsonCodec.hasCount(reported, "output_tokens");
            var updates = events.stream().filter(ModelEvent.UsageUpdate.class::isInstance)
                    .map(ModelEvent.UsageUpdate.class::cast).toList();
            assertEquals(hasUsageObject ? 1 : 0, updates.size());
            assertEquals(hasInput, turn.usage().uncachedInputKnown());
            assertEquals(hasOutput, turn.usage().outputKnown());
            assertEquals(hasInput ? reported.get("input_tokens").getAsLong() : 0,
                    turn.usage().uncachedInputTokens());
            assertEquals(hasOutput ? reported.get("output_tokens").getAsLong() : 0,
                    turn.usage().outputTokens());
            assertFalse(turn.usage().inputKnown(), "total input needs all reported cache categories");
            assertFalse(turn.usage().cacheReadKnown());
            assertFalse(turn.usage().cacheWriteKnown());
            if (hasUsageObject) assertEquals(turn.usage(), updates.getFirst().usage());
        }
    }

    @Test
    void streamingPreservesInputAndOutputPresenceIndependently() {
        for (String input : List.of("null", "{}", "{\"input_tokens\":0}")) {
            for (String output : List.of("null", "{}", "{\"output_tokens\":0}")) {
                List<ModelEvent> events = new ArrayList<>();
                AnthropicStreamAccumulator accumulator = new AnthropicStreamAccumulator(events::add);
                accumulator.accept(new SseEvent("message_start", "{\"message\":{\"model\":\"claude-test\",\"usage\":" + input + "}}"));
                accumulator.accept(new SseEvent("message_delta", "{\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":" + output + "}"));
                var turn = accumulator.finish();
                var updates = events.stream().filter(ModelEvent.UsageUpdate.class::isInstance)
                        .map(ModelEvent.UsageUpdate.class::cast).toList();
                boolean hasUsageObject = !input.equals("null") || !output.equals("null");
                assertEquals(hasUsageObject, !updates.isEmpty());
                assertEquals(input.contains("input_tokens"), turn.usage().uncachedInputKnown());
                assertEquals(output.contains("output_tokens"), turn.usage().outputKnown());
                assertEquals(0, turn.usage().uncachedInputTokens());
                assertEquals(0, turn.usage().outputTokens());
                assertFalse(turn.usage().inputKnown(), "missing cache counts are not known zero");
                assertFalse(turn.usage().cacheReadKnown());
                assertFalse(turn.usage().cacheWriteKnown());
                if (hasUsageObject) assertEquals(turn.usage(), updates.getLast().usage());
                assertTrue(events.stream().anyMatch(ModelEvent.MessageComplete.class::isInstance));
            }
        }
    }

}
