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
    void jsonReportsOnlyRealCompleteCountsIncludingExplicitZero() {
        for (String raw : List.of("missing", "null", "{}", "{\"input_tokens\":3}",
                "{\"input_tokens\":null,\"output_tokens\":1}",
                "{\"input_tokens\":0,\"output_tokens\":0}",
                "{\"input_tokens\":3,\"output_tokens\":1,\"extra_field\":42}")) {
            JsonObject response = JsonParser.parseString("""
                    {"model":"claude-test","stop_reason":"end_turn","content":[{"type":"text","text":"OK"}]}
                    """).getAsJsonObject();
            if (!raw.equals("missing")) response.add("usage", JsonParser.parseString(raw));
            List<ModelEvent> events = new ArrayList<>();
            new AnthropicJsonCodec(new Gson()).parseTurn(response.toString(), events::add);
            assertEquals(raw.contains("\"input_tokens\":0") || raw.contains("extra_field"),
                    events.stream().anyMatch(ModelEvent.UsageUpdate.class::isInstance));
        }
    }

    @Test
    void streamingNeedsInputAtStartAndOutputAtFinishWithoutInventingMissingCounters() {
        for (String input : List.of("null", "{}", "{\"input_tokens\":0}")) {
            for (String output : List.of("null", "{}", "{\"output_tokens\":0}")) {
                List<ModelEvent> events = new ArrayList<>();
                AnthropicStreamAccumulator accumulator = new AnthropicStreamAccumulator(events::add);
                accumulator.accept(new SseEvent("message_start", "{\"message\":{\"model\":\"claude-test\",\"usage\":" + input + "}}"));
                accumulator.accept(new SseEvent("message_delta", "{\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":" + output + "}"));
                accumulator.finish();
                assertEquals(input.contains("input_tokens") && output.contains("output_tokens"),
                        events.stream().anyMatch(ModelEvent.UsageUpdate.class::isInstance));
                assertTrue(events.stream().anyMatch(ModelEvent.MessageComplete.class::isInstance));
            }
        }
    }
}
