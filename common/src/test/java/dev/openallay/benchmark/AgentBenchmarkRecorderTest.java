package dev.openallay.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.agent.trace.LiveAgentTrace;
import dev.openallay.agent.trace.LiveTraceEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AgentBenchmarkRecorderTest {
    @Test
    void derivesCanonicalEnvelopeAndSemanticCountersFromProductionEvents() {
        AgentBenchmarkRecorder recorder = new AgentBenchmarkRecorder();
        recorder.accept(new AgentEvent.ToolCompleted(
                "skill-1", "openallay:load_skill", false,
                normalizedSkill("COMPLETE", 0)));
        recorder.accept(new AgentEvent.ToolCompleted(
                "js-1", "openallay:run_javascript", true,
                failure("javascript_error")));
        recorder.accept(new AgentEvent.ToolCompleted(
                "js-2", "openallay:run_javascript", false,
                success("{\"winner\":\"minecraft:netherite_sword\"}")));
        recorder.accept(new AgentEvent.ToolCompleted(
                "skill-2", "openallay:load_skill", false,
                normalizedSkill("ALREADY_LOADED", 0)));
        recorder.effect("enchanted-item-created");

        BenchmarkOutcome outcome = recorder.outcome(new AgentResult(
                AgentState.COMPLETED,
                "The winner is minecraft:netherite_sword",
                null,
                null,
                trace()));

        assertTrue(outcome.canonicalResult().toString()
                .contains("minecraft:netherite_sword"));
        assertEquals(List.of("enchanted-item-created"), outcome.observedEffects());
        assertEquals(2, outcome.metrics().modelTurns());
        assertEquals(4, outcome.metrics().toolCalls());
        assertEquals(2, outcome.metrics().javascriptCalls());
        assertEquals(1, outcome.metrics().skillLoads());
        assertEquals(1, outcome.metrics().duplicateSkillLoads());
        assertEquals(1, outcome.metrics().invalidCalls());
        assertEquals(1, outcome.metrics().correctedCalls());
    }

    private static LiveAgentTrace trace() {
        return new LiveAgentTrace(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "benchmark",
                Instant.EPOCH,
                Instant.EPOCH,
                AgentState.COMPLETED,
                List.of(
                        event("model_turn", new JsonObject()),
                        event("tool_call", tool("openallay:load_skill")),
                        event("tool_call", tool("openallay:run_javascript")),
                        event("model_turn", new JsonObject()),
                        event("tool_call", tool("openallay:run_javascript")),
                        event("tool_call", tool("openallay:load_skill"))),
                "done",
                null);
    }

    private static LiveTraceEvent event(String type, JsonObject payload) {
        return new LiveTraceEvent(type, 0, payload);
    }

    private static JsonObject tool(String id) {
        JsonObject value = new JsonObject();
        value.addProperty("toolId", id);
        return value;
    }

    private static JsonObject normalizedSkill(String state, int offset) {
        JsonObject value = new JsonObject();
        value.addProperty("state", state);
        value.addProperty("offset", offset);
        JsonObject normalized = new JsonObject();
        normalized.addProperty("status", "success");
        normalized.add("value", value);
        return normalized;
    }

    private static JsonObject success(String preview) {
        JsonObject value = new JsonObject();
        value.add("preview", JsonParser.parseString(preview));
        JsonObject normalized = new JsonObject();
        normalized.addProperty("status", "success");
        normalized.add("value", value);
        return normalized;
    }

    private static JsonObject failure(String code) {
        JsonObject normalized = new JsonObject();
        normalized.addProperty("status", "failure");
        normalized.addProperty("code", code);
        normalized.addProperty("message", "failed");
        return normalized;
    }
}
