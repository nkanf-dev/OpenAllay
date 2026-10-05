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
        this.gson = dev.openallay.json.EngineJson.withInstant(Objects.requireNonNull(gson, "gson"));
    }

    public ServerAgentEventPayload encode(UUID requestId, AgentEvent event) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(event, "event");
        String type = type(event);
        boolean terminal = event instanceof AgentEvent.FinalText || event instanceof AgentEvent.Failed;
        String eventJson;
        if (event instanceof AgentEvent.SteerApplied applied) {
            JsonObject body = new JsonObject();
            body.addProperty("messageId", applied.messageId().toString());
            body.add("message", BridgeJsonCodec.encodeHistoryMessage(gson, ServerAgentHistoryMessage.from(applied.message())));
            eventJson = body.toString();
        } else if (event instanceof AgentEvent.ContextCompacted compacted) {
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
            BridgeJsonCodec.rejectDuplicateFields(payload.eventJson());
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
            case "steer_applied" -> readSteerApplied(body);
            case "steer_rejected" -> new AgentEvent.SteerRejected(readMessageId(body, Set.of("messageId")));
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

    private AgentEvent.SteerApplied readSteerApplied(JsonObject body) {
        UUID messageId = readMessageId(body, Set.of("messageId", "message"));
        BridgeJsonCodec.validateHistoryMessage(body.get("message"));
        ServerAgentHistoryMessage message = gson.fromJson(body.get("message"), ServerAgentHistoryMessage.class);
        ServerAgentSteerPayload.validateMessage(message);
        return new AgentEvent.SteerApplied(messageId, message.toModelMessage());
    }

    private UUID readMessageId(JsonObject body, Set<String> fields) {
        if (!body.keySet().equals(fields) || body.get("messageId") == null
                || !body.get("messageId").isJsonPrimitive()
                || !body.getAsJsonPrimitive("messageId").isString()) {
            throw new IllegalArgumentException("Server steer event schema mismatch");
        }
        return UUID.fromString(body.get("messageId").getAsString());
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
        Objects.requireNonNull(event);
        if (event instanceof AgentEvent.StateChanged) return "state";
        if (event instanceof AgentEvent.ContextCompacted) return "context_compacted";
        if (event instanceof AgentEvent.ContextUpdated) return "context_updated";
        if (event instanceof AgentEvent.ContextFinalized) return "context_finalized";
        if (event instanceof AgentEvent.SteerApplied) return "steer_applied";
        if (event instanceof AgentEvent.SteerRejected) return "steer_rejected";
        if (event instanceof AgentEvent.ToolStarted) return "tool_started";
        if (event instanceof AgentEvent.ToolCompleted) return "tool_completed";
        if (event instanceof AgentEvent.FinalText) return "final_text";
        if (event instanceof AgentEvent.Failed) return "failed";
        if (event instanceof AgentEvent.RequestReleased) return "request_released";
        if (event instanceof AgentEvent.ModelUsageStarted) return "model_usage_started";
        if (event instanceof AgentEvent.ModelUsageObserved) return "model_usage_observed";
        if (event instanceof AgentEvent.ModelProgress progress) {
            ModelEvent modelEvent = Objects.requireNonNull(progress.event());
            if (modelEvent instanceof ModelEvent.TextDelta) return "text_delta";
            if (modelEvent instanceof ModelEvent.ReasoningDelta) return "reasoning_delta";
            if (modelEvent instanceof ModelEvent.ToolUseComplete) return "tool_use_complete";
            if (modelEvent instanceof ModelEvent.UsageUpdate) return "usage";
            if (modelEvent instanceof ModelEvent.UsageStarted) {
                throw new IllegalArgumentException("Usage starts must use AgentEvent.ModelUsageStarted");
            }
            if (modelEvent instanceof ModelEvent.UsageObserved) {
                throw new IllegalArgumentException("Usage receipts must use AgentEvent.ModelUsageObserved");
            }
            if (modelEvent instanceof ModelEvent.AttemptStarted) return "model_attempt_started";
            if (modelEvent instanceof ModelEvent.ResponseStarted) return "model_response_started";
            if (modelEvent instanceof ModelEvent.RateLimited) return "rate_limited";
            if (modelEvent instanceof ModelEvent.MessageComplete) return "model_complete";
            if (modelEvent instanceof ModelFailure) return "model_failure";
            throw new IncompatibleClassChangeError();
        }
        throw new IncompatibleClassChangeError();
    }
}
