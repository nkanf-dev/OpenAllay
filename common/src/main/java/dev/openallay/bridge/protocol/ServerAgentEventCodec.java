package dev.openallay.bridge.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.context.ContextCheckpointCodec;
import dev.openallay.guide.GuideToolMessageCodec;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelFailure;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Strict common wire codec for request-correlated server Agent events. */
public final class ServerAgentEventCodec {
    private final Gson gson;
    private final ContextCheckpointCodec checkpoints = new ContextCheckpointCodec();
    private final dev.openallay.agent.context.ModelContextCodec contexts =
            new dev.openallay.agent.context.ModelContextCodec();

    public ServerAgentEventCodec(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public ServerAgentEventPayload encode(UUID requestId, AgentEvent event) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(event, "event");
        String type = type(event);
        boolean terminal = event instanceof AgentEvent.FinalText || event instanceof AgentEvent.Failed;
        String eventJson;
        if (event instanceof AgentEvent.ContextCompacted compacted) {
            eventJson = checkpoints.encode(compacted.checkpoint());
        } else if (event instanceof AgentEvent.ContextUpdated updated) {
            JsonObject context = new JsonObject();
            context.add("messages", JsonParser.parseString(contexts.encode(updated.messages())));
            context.add("requestMessages", JsonParser.parseString(contexts.encode(updated.requestMessages())));
            eventJson = context.toString();
        } else if (event instanceof AgentEvent.ContextFinalized finalized) {
            JsonObject context = new JsonObject();
            context.add("messages", JsonParser.parseString(contexts.encode(finalized.messages())));
            context.add("requestMessages", JsonParser.parseString(contexts.encode(finalized.requestMessages())));
            eventJson = context.toString();
        } else if (event instanceof AgentEvent.ToolStarted started) {
            eventJson = encodeToolStarted(started).toString();
        } else {
            Object body = event instanceof AgentEvent.ModelProgress progress ? progress.event() : event;
            eventJson = gson.toJson(body);
        }
        return new ServerAgentEventPayload(
                requestId, type, eventJson, terminal);
    }

    public AgentEvent decode(ServerAgentEventPayload payload, UUID expectedRequestId) {
        Objects.requireNonNull(payload, "payload");
        if (!payload.requestId().equals(expectedRequestId)) {
            throw new IllegalArgumentException("Server Agent event request ID mismatch");
        }
        JsonObject body;
        try {
            var parsed = JsonParser.parseString(payload.eventJson());
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Server Agent event body must be an object");
            }
            body = parsed.getAsJsonObject();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Malformed server Agent event JSON", failure);
        }

        AgentEvent event = switch (payload.eventType()) {
            case "state" -> new AgentEvent.StateChanged(read(body, Set.of("state"), AgentEvent.StateChanged.class).state());
            case "context_compacted" ->
                    new AgentEvent.ContextCompacted(checkpoints.decode(body.toString()));
            case "context_updated" -> readContext(body);
            case "context_finalized" -> readFinalized(body);
            case "text_delta" -> new AgentEvent.ModelProgress(
                    read(body, Set.of("text"), ModelEvent.TextDelta.class));
            case "reasoning_delta" -> new AgentEvent.ModelProgress(
                    read(body, Set.of("text"), ModelEvent.ReasoningDelta.class));
            case "tool_use_complete" -> new AgentEvent.ModelProgress(
                    read(body, Set.of("id", "name", "input"), ModelEvent.ToolUseComplete.class));
            case "request_released" -> read(body, Set.of(), AgentEvent.RequestReleased.class);
            case "model_usage_started" -> readUsageStarted(body);
            case "model_usage_observed" -> readUsageObserved(body);
            case "usage" -> new AgentEvent.ModelProgress(
                    read(body, Set.of("usage"), ModelEvent.UsageUpdate.class));
            case "model_attempt_started" -> new AgentEvent.ModelProgress(
                    readAttemptStarted(body));
            case "model_response_started" -> new AgentEvent.ModelProgress(
                    read(body, Set.of(), ModelEvent.ResponseStarted.class));
            case "rate_limited" -> new AgentEvent.ModelProgress(
                    read(body, Set.of("retryAfterMillis", "attempt"), ModelEvent.RateLimited.class));
            case "model_complete" -> new AgentEvent.ModelProgress(
                    read(body, Set.of("stopReason"), ModelEvent.MessageComplete.class));
            case "model_failure" -> new AgentEvent.ModelProgress(readModelFailure(body));
            case "tool_started" -> readToolStarted(body);
            case "tool_completed" -> read(
                    body,
                    Set.of("invocationId", "toolId", "failure", "normalized"),
                    AgentEvent.ToolCompleted.class);
            case "final_text" -> read(body, Set.of("text"), AgentEvent.FinalText.class);
            case "failed" -> read(body, Set.of("code", "message"), AgentEvent.Failed.class);
            default -> throw new IllegalArgumentException(
                    "Unknown server Agent event type " + payload.eventType());
        };
        boolean expectedTerminal = event instanceof AgentEvent.FinalText || event instanceof AgentEvent.Failed;
        if (payload.terminal() != expectedTerminal) {
            throw new IllegalArgumentException("Server Agent event terminal flag is inconsistent");
        }
        return event;
    }

    private AgentEvent.ModelUsageStarted readUsageStarted(JsonObject body) {
        if (!body.keySet().equals(Set.of("callId", "modelIdentifier"))
                || !body.get("callId").isJsonPrimitive()
                || !body.getAsJsonPrimitive("callId").isString()
                || !body.get("modelIdentifier").isJsonPrimitive()
                || !body.getAsJsonPrimitive("modelIdentifier").isString()) {
            throw new IllegalArgumentException("Server model usage start schema mismatch");
        }
        return new AgentEvent.ModelUsageStarted(UUID.fromString(body.get("callId").getAsString()),
                body.get("modelIdentifier").getAsString());
    }

    private AgentEvent.ModelUsageObserved readUsageObserved(JsonObject body) {
        if (!body.keySet().equals(Set.of("callId", "modelIdentifier", "usage"))
                || !body.get("callId").isJsonPrimitive()
                || !body.getAsJsonPrimitive("callId").isString()
                || !body.get("modelIdentifier").isJsonPrimitive()
                || !body.getAsJsonPrimitive("modelIdentifier").isString()
                || !body.get("usage").isJsonObject()) {
            throw new IllegalArgumentException("Server model usage receipt schema mismatch");
        }
        JsonObject usage = body.getAsJsonObject("usage");
        Set<String> counts = Set.of("inputTokens", "outputTokens", "cacheReadTokens",
                "cacheWriteTokens", "uncachedInputTokens");
        Set<String> known = Set.of("inputKnown", "outputKnown", "cacheReadKnown",
                "cacheWriteKnown", "uncachedInputKnown");
        java.util.HashSet<String> fields = new java.util.HashSet<>(counts);
        fields.addAll(known);
        if (!usage.keySet().equals(fields)) {
            throw new IllegalArgumentException("Server model usage fields mismatch");
        }
        for (String field : counts) {
            if (!usage.get(field).isJsonPrimitive() || !usage.getAsJsonPrimitive(field).isNumber()) {
                throw new IllegalArgumentException("Server model usage count must be an integer");
            }
            try {
                if (usage.get(field).getAsBigDecimal().longValueExact() < 0) {
                    throw new IllegalArgumentException("Server model usage count must be nonnegative");
                }
            } catch (ArithmeticException invalid) {
                throw new IllegalArgumentException("Server model usage count is invalid", invalid);
            }
        }
        for (String field : known) {
            if (!usage.get(field).isJsonPrimitive() || !usage.getAsJsonPrimitive(field).isBoolean()) {
                throw new IllegalArgumentException("Server model usage presence must be boolean");
            }
        }
        return new AgentEvent.ModelUsageObserved(UUID.fromString(body.get("callId").getAsString()),
                body.get("modelIdentifier").getAsString(),
                gson.fromJson(usage, dev.openallay.model.ModelUsage.class));
    }

    private AgentEvent.ContextUpdated readContext(JsonObject body) {
        if (!body.keySet().equals(Set.of("messages", "requestMessages"))) {
            throw new IllegalArgumentException("Server model context schema mismatch");
        }
        return new AgentEvent.ContextUpdated(contexts.decode(body.get("messages").toString()),
                contexts.decode(body.get("requestMessages").toString()));
    }

    private AgentEvent.ContextFinalized readFinalized(JsonObject body) {
        if (!body.keySet().equals(Set.of("messages", "requestMessages"))) {
            throw new IllegalArgumentException("Server finalized context schema mismatch");
        }
        return new AgentEvent.ContextFinalized(contexts.decode(body.get("messages").toString()),
                contexts.decode(body.get("requestMessages").toString()));
    }

    private <T> T read(JsonObject body, Set<String> fields, Class<T> type) {
        if (!body.keySet().equals(fields)) {
            throw new IllegalArgumentException(
                    "Server Agent event schema mismatch for " + type.getSimpleName());
        }
        T value = gson.fromJson(body, type);
        if (value == null) {
            throw new IllegalArgumentException("Server Agent event decoded to null");
        }
        return value;
    }

    private ModelFailure readModelFailure(JsonObject body) {
        Set<String> fields = body.keySet();
        if (!fields.equals(Set.of("code", "message"))
                && !fields.equals(Set.of("code", "message", "httpStatus"))) {
            throw new IllegalArgumentException("Server Agent event schema mismatch for ModelFailure");
        }
        return gson.fromJson(body, ModelFailure.class);
    }

    private ModelEvent.AttemptStarted readAttemptStarted(JsonObject body) {
        if (!body.keySet().equals(Set.of("attempt"))
                && !body.keySet().equals(Set.of("attempt", "attemptTimeoutMillis"))) {
            throw new IllegalArgumentException(
                    "Server Agent event schema mismatch for AttemptStarted");
        }
        return gson.fromJson(body, ModelEvent.AttemptStarted.class);
    }

    private static JsonObject encodeToolStarted(AgentEvent.ToolStarted started) {
        JsonObject body = new JsonObject();
        body.addProperty("invocationId", started.invocationId());
        body.addProperty("toolId", started.toolId());
        body.add("presentationMessages", GuideToolMessageCodec.encode(
                started.presentationMessages()));
        return body;
    }

    private static AgentEvent.ToolStarted readToolStarted(JsonObject body) {
        if (!body.keySet().equals(Set.of(
                "invocationId", "toolId", "presentationMessages"))) {
            throw new IllegalArgumentException("Server Tool start schema mismatch");
        }
        if (!body.get("invocationId").isJsonPrimitive()
                || !body.getAsJsonPrimitive("invocationId").isString()
                || !body.get("toolId").isJsonPrimitive()
                || !body.getAsJsonPrimitive("toolId").isString()) {
            throw new IllegalArgumentException("Server Tool start identity types are invalid");
        }
        return new AgentEvent.ToolStarted(
                body.get("invocationId").getAsString(),
                body.get("toolId").getAsString(),
                GuideToolMessageCodec.decode(body.get("presentationMessages")));
    }

    private static String type(AgentEvent event) {
        return switch (event) {
            case AgentEvent.StateChanged ignored -> "state";
            case AgentEvent.ContextCompacted ignored -> "context_compacted";
            case AgentEvent.ContextUpdated ignored -> "context_updated";
            case AgentEvent.ContextFinalized ignored -> "context_finalized";
            case AgentEvent.ToolStarted ignored -> "tool_started";
            case AgentEvent.ToolCompleted ignored -> "tool_completed";
            case AgentEvent.FinalText ignored -> "final_text";
            case AgentEvent.Failed ignored -> "failed";
            case AgentEvent.RequestReleased ignored -> "request_released";
            case AgentEvent.ModelUsageStarted ignored -> "model_usage_started";
            case AgentEvent.ModelUsageObserved ignored -> "model_usage_observed";
            case AgentEvent.ModelProgress progress -> switch (progress.event()) {
                case ModelEvent.TextDelta ignored -> "text_delta";
                case ModelEvent.ReasoningDelta ignored -> "reasoning_delta";
                case ModelEvent.ToolUseComplete ignored -> "tool_use_complete";
                case ModelEvent.UsageUpdate ignored -> "usage";
                case ModelEvent.UsageStarted ignored -> throw new IllegalArgumentException(
                        "Usage starts must use AgentEvent.ModelUsageStarted");
                case ModelEvent.UsageObserved ignored -> throw new IllegalArgumentException(
                        "Usage receipts must use AgentEvent.ModelUsageObserved");
                case ModelEvent.AttemptStarted ignored -> "model_attempt_started";
                case ModelEvent.ResponseStarted ignored -> "model_response_started";
                case ModelEvent.RateLimited ignored -> "rate_limited";
                case ModelEvent.MessageComplete ignored -> "model_complete";
                case ModelFailure ignored -> "model_failure";
            };
        };
    }
}
