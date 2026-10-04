package dev.openallay.model.anthropic;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.model.ModelEvent;
import dev.openallay.model.http.SseEvent;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class AnthropicStreamAccumulatorUsageTest {
    @Test
    void mergesInputCachePresenceAndCumulativeOutputWithoutCountingCacheCreationAsHit() {
        var events = new ArrayList<ModelEvent>();
        var stream = new AnthropicStreamAccumulator(events::add);
        stream.accept(event("""
                {"type":"message_start","message":{"model":"claude-test","usage":{
                "input_tokens":100,"cache_read_input_tokens":50,"cache_creation_input_tokens":25,"output_tokens":0}}}
                """));
        stream.accept(event("""
                {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":5}}
                """));
        var usage = stream.finish().usage();
        assertEquals(175, usage.inputTokens());
        assertEquals(50, usage.cacheReadTokens());
        assertEquals(25, usage.cacheWriteTokens());
        assertEquals(5, usage.outputTokens());
        assertTrue(usage.complete());
        assertTrue(events.stream().anyMatch(ModelEvent.UsageUpdate.class::isInstance));
    }

    @Test
    void missingCacheFieldsStayUnknownAndPartialUsageIsPublishedBeforeStreamFailure() {
        var events = new ArrayList<ModelEvent>();
        var stream = new AnthropicStreamAccumulator(events::add);
        stream.accept(event("""
                {"type":"message_start","message":{"model":"claude-test","usage":{"input_tokens":100}}}
                """));
        var reported = (ModelEvent.UsageUpdate) events.getFirst();
        assertTrue(reported.usage().uncachedInputKnown());
        assertFalse(reported.usage().inputKnown());
        assertFalse(reported.usage().cacheReadKnown());
        assertFalse(reported.usage().cacheWriteKnown());
        assertThrows(IllegalArgumentException.class, stream::finish);
    }

    private static SseEvent event(String json) { return new SseEvent(null, json); }
}
