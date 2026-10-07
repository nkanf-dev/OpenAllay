package dev.openallay.agent.trace;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.agent.AgentRequest;
import dev.openallay.agent.AgentState;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelTurn;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class LiveAgentTraceRecorder {
    private final Gson gson;
    private final AgentRequest request;
    private final Instant startedAt = Instant.now();
    private final long startedNanos = System.nanoTime();
    private final List<LiveTraceEvent> events = new ArrayList<>();

    public LiveAgentTraceRecorder(Gson gson, AgentRequest request) {
        this.gson = dev.openallay.json.EngineJson.withInstant(gson);
        this.request = request;
        JsonObject initial = new JsonObject();
        initial.addProperty("userMessage", request.userMessage());
        initial.add("userInput", gson.toJsonTree(request.userInput()));
        add("request", initial);
    }

    public synchronized void state(AgentState state) {
        JsonObject payload = new JsonObject();
        payload.addProperty("state", state.name());
        add("state", payload);
    }

    public synchronized void modelTurn(ModelTurn turn) {
        ModelTurn visible = new ModelTurn(turn.providerId(), turn.model(),
                dev.openallay.util.Java8Collections.toList(turn.content().stream()
                        .filter(content -> !(content instanceof dev.openallay.model.ModelContent.Reasoning))),
                turn.stopReason(), turn.usage());
        add("model_turn", gson.toJsonTree(visible));
    }

    public synchronized void modelRequest(ModelRequest request) {
        // Do not serialize the request-only resolver. It may close over scoped stores or runtime state.
        JsonObject payload = new JsonObject();
        payload.addProperty("systemPrompt", request.systemPrompt());
        payload.add("messages", gson.toJsonTree(request.messages()));
        payload.add("tools", gson.toJsonTree(request.tools()));
        payload.addProperty("stream", request.stream());
        payload.addProperty("sessionKey", request.sessionKey());
        if (request.maxOutputTokens() != null) {
            payload.addProperty("maxOutputTokens", request.maxOutputTokens());
        }
        add("model_request", payload);
    }

    public synchronized void toolCall(String toolId, JsonObject arguments) {
        JsonObject payload = new JsonObject();
        payload.addProperty("toolId", toolId);
        payload.add("arguments", dev.openallay.json.JsonTrees.copy(arguments));
        add("tool_call", payload);
    }

    public synchronized void toolResult(AgentToolResult result) {
        JsonObject payload = new JsonObject();
        payload.addProperty("toolId", result.toolId());
        payload.addProperty("failure", result.failure());
        payload.add("result", result.normalized());
        add("tool_result", payload);
    }

    public synchronized void failure(String code, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("code", code);
        payload.addProperty("message", message);
        add("failure", payload);
    }

    public synchronized LiveAgentTrace finish(
            AgentState state, String finalText, String errorCode) {
        return new LiveAgentTrace(
                request.requestId(),
                request.actorId(),
                request.sessionId(),
                startedAt,
                Instant.now(),
                state,
                dev.openallay.util.Java8Collections.listCopyOf(events),
                finalText,
                errorCode);
    }

    private void add(String type, com.google.gson.JsonElement payload) {
        events.add(new LiveTraceEvent(type, System.nanoTime() - startedNanos, payload));
    }
}
