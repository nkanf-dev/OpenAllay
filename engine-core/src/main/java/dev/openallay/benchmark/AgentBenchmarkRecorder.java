package dev.openallay.benchmark;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.trace.LiveAgentTrace;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Builds one benchmark outcome from the same events and provider-neutral trace emitted by the
 * production Agent loop.
 *
 * <p>The canonical envelope contains the final answer and complete normalized Tool results. The
 * verifier consumes this envelope outside model context; no expected answer is injected into the
 * prompt.
 */
public final class AgentBenchmarkRecorder implements Consumer<AgentEvent> {
    private static final String RUN_JAVASCRIPT = "openallay:run_javascript";
    private static final String LOAD_SKILL = "openallay:load_skill";

    private final List<AgentEvent.ToolCompleted> completed = new ArrayList<>();
    private final List<String> effects = new ArrayList<>();

    @Override
    public synchronized void accept(AgentEvent event) {
        if (event instanceof AgentEvent.ToolCompleted tool) {
            completed.add(tool);
        }
    }

    public synchronized void effect(String effect) {
        if (effect != null && !effect.isBlank()) {
            effects.add(effect.strip());
        }
    }

    public synchronized BenchmarkOutcome outcome(AgentResult result) {
        JsonObject canonical = new JsonObject();
        canonical.addProperty("answer", result.text() == null ? "" : result.text());
        JsonArray toolResults = new JsonArray();
        for (AgentEvent.ToolCompleted tool : completed) {
            JsonObject entry = new JsonObject();
            entry.addProperty("toolId", tool.toolId());
            entry.addProperty("failure", tool.failure());
            entry.add("normalized", tool.normalized());
            toolResults.add(entry);
        }
        canonical.add("toolResults", toolResults);

        int modelTurns = countTrace(result.trace(), "model_turn");
        int toolCalls = countTrace(result.trace(), "tool_call");
        int javascriptCalls = countTraceTool(result.trace(), RUN_JAVASCRIPT);
        int skillLoads = 0;
        int skillReloads = 0;
        int duplicateSkillLoads = 0;
        int invalidCalls = 0;
        int correctedCalls = 0;
        Set<String> failedTools = new HashSet<>();
        for (AgentEvent.ToolCompleted tool : completed) {
            if (tool.failure()) {
                invalidCalls++;
                failedTools.add(tool.toolId());
            } else if (failedTools.remove(tool.toolId())) {
                correctedCalls++;
            }
            if (!LOAD_SKILL.equals(tool.toolId()) || tool.failure()) {
                continue;
            }
            JsonObject value = value(tool.normalized());
            String state = string(value, "state");
            if ("REHYDRATED".equals(state)) {
                skillReloads++;
            } else if ("ALREADY_LOADED".equals(state)) {
                duplicateSkillLoads++;
            } else if (("CONTENT".equals(state) || "COMPLETE".equals(state))
                    && integer(value, "offset") == 0) {
                skillLoads++;
            }
        }
        String terminalCode = result.successful()
                ? "completed"
                : result.errorCode() == null ? "failed" : result.errorCode();
        return new BenchmarkOutcome(
                canonical,
                List.copyOf(effects),
                new BenchmarkMetrics(
                        result.successful(),
                        modelTurns,
                        toolCalls,
                        javascriptCalls,
                        skillLoads,
                        skillReloads,
                        duplicateSkillLoads,
                        invalidCalls,
                        correctedCalls,
                        terminalCode));
    }

    private static JsonObject value(JsonObject normalized) {
        return normalized.has("value") && normalized.get("value").isJsonObject()
                ? normalized.getAsJsonObject("value")
                : new JsonObject();
    }

    private static String string(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonPrimitive()
                ? object.get(field).getAsString()
                : "";
    }

    private static int integer(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonPrimitive()
                ? object.get(field).getAsInt()
                : -1;
    }

    private static int countTrace(LiveAgentTrace trace, String type) {
        return trace == null
                ? 0
                : Math.toIntExact(trace.events().stream()
                        .filter(event -> type.equals(event.type()))
                        .count());
    }

    private static int countTraceTool(LiveAgentTrace trace, String toolId) {
        if (trace == null) {
            return 0;
        }
        return Math.toIntExact(trace.events().stream()
                .filter(event -> "tool_call".equals(event.type()))
                .filter(event -> event.payload() != null && event.payload().isJsonObject())
                .filter(event -> toolId.equals(string(
                        event.payload().getAsJsonObject(), "toolId")))
                .count());
    }
}
