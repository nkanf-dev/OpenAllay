package dev.openallay.bridge.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
        final class $oaPattern0_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.SteerApplied bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = event) instanceof dev.openallay.agent.AgentEvent.SteerApplied && (($oaPattern0_holder.bound = (AgentEvent.SteerApplied) $oaPattern0_holder.value) != null))) {
            JsonObject body = new JsonObject();
            body.addProperty("messageId", $oaPattern0_holder.bound.messageId().toString());
            body.add("message", BridgeJsonCodec.encodeHistoryMessage(gson, ServerAgentHistoryMessage.from($oaPattern0_holder.bound.message())));
            eventJson = body.toString();
        } else {
final class $oaPattern1_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ContextCompacted bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ContextCompacted && (($oaPattern1_holder.bound = (AgentEvent.ContextCompacted) $oaPattern1_holder.value) != null))) {
            eventJson = checkpoints.encode($oaPattern1_holder.bound.checkpoint());
        } else {
final class $oaPattern2_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ContextUpdated bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ContextUpdated && (($oaPattern2_holder.bound = (AgentEvent.ContextUpdated) $oaPattern2_holder.value) != null))) {
            JsonObject context = new JsonObject();
            context.add("messages", dev.openallay.json.JsonTrees.parse(contexts.encode($oaPattern2_holder.bound.messages())));
            context.add("requestMessages", dev.openallay.json.JsonTrees.parse(contexts.encode($oaPattern2_holder.bound.requestMessages())));
            eventJson = context.toString();
        } else {
final class $oaPattern3_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ContextFinalized bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ContextFinalized && (($oaPattern3_holder.bound = (AgentEvent.ContextFinalized) $oaPattern3_holder.value) != null))) {
            JsonObject context = new JsonObject();
            context.add("messages", dev.openallay.json.JsonTrees.parse(contexts.encode($oaPattern3_holder.bound.messages())));
            context.add("requestMessages", dev.openallay.json.JsonTrees.parse(contexts.encode($oaPattern3_holder.bound.requestMessages())));
            eventJson = context.toString();
        } else {
final class $oaPattern4_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ToolStarted bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ToolStarted && (($oaPattern4_holder.bound = (AgentEvent.ToolStarted) $oaPattern4_holder.value) != null))) {
            eventJson = encodeToolStarted($oaPattern4_holder.bound).toString();
        } else {
            final class $oaPattern5_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ModelProgress bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
Object body = (($oaPattern5_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ModelProgress && (($oaPattern5_holder.bound = (AgentEvent.ModelProgress) $oaPattern5_holder.value) != null)) ? $oaPattern5_holder.bound.event() : event;
            eventJson = gson.toJson(body);
        }
}
}
}
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
            com.google.gson.JsonElement parsed = dev.openallay.json.JsonTrees.parse(payload.eventJson());
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Server Agent event body must be an object");
            }
            body = parsed.getAsJsonObject();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Malformed server Agent event JSON", failure);
        }

        dev.openallay.agent.AgentEvent $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((payload.eventType())) {
case "state":
{
$oaSwitch0_exit_result = new AgentEvent.StateChanged(read(body, dev.openallay.util.Java8Collections.setOf("state"), AgentEvent.StateChanged.class).state()); break $oaSwitch0_exit;
}
case "context_compacted":
{
$oaSwitch0_exit_result = new AgentEvent.ContextCompacted(checkpoints.decode(body.toString())); break $oaSwitch0_exit;
}
case "context_updated":
{
$oaSwitch0_exit_result = readContext(body); break $oaSwitch0_exit;
}
case "context_finalized":
{
$oaSwitch0_exit_result = readFinalized(body); break $oaSwitch0_exit;
}
case "steer_applied":
{
$oaSwitch0_exit_result = readSteerApplied(body); break $oaSwitch0_exit;
}
case "steer_rejected":
{
$oaSwitch0_exit_result = new AgentEvent.SteerRejected(readMessageId(body, dev.openallay.util.Java8Collections.setOf("messageId"))); break $oaSwitch0_exit;
}
case "text_delta":
{
$oaSwitch0_exit_result = new AgentEvent.ModelProgress(
                    read(body, dev.openallay.util.Java8Collections.setOf("text"), ModelEvent.TextDelta.class)); break $oaSwitch0_exit;
}
case "reasoning_delta":
{
$oaSwitch0_exit_result = new AgentEvent.ModelProgress(
                    read(body, dev.openallay.util.Java8Collections.setOf("text"), ModelEvent.ReasoningDelta.class)); break $oaSwitch0_exit;
}
case "tool_use_complete":
{
$oaSwitch0_exit_result = new AgentEvent.ModelProgress(
                    read(body, dev.openallay.util.Java8Collections.setOf("id", "name", "input"), ModelEvent.ToolUseComplete.class)); break $oaSwitch0_exit;
}
case "request_released":
{
$oaSwitch0_exit_result = read(body, dev.openallay.util.Java8Collections.setOf(), AgentEvent.RequestReleased.class); break $oaSwitch0_exit;
}
case "model_usage_started":
{
$oaSwitch0_exit_result = readUsageStarted(body); break $oaSwitch0_exit;
}
case "model_usage_observed":
{
$oaSwitch0_exit_result = readUsageObserved(body); break $oaSwitch0_exit;
}
case "usage":
{
$oaSwitch0_exit_result = new AgentEvent.ModelProgress(
                    read(body, dev.openallay.util.Java8Collections.setOf("usage"), ModelEvent.UsageUpdate.class)); break $oaSwitch0_exit;
}
case "model_attempt_started":
{
$oaSwitch0_exit_result = new AgentEvent.ModelProgress(
                    readAttemptStarted(body)); break $oaSwitch0_exit;
}
case "model_response_started":
{
$oaSwitch0_exit_result = new AgentEvent.ModelProgress(
                    read(body, dev.openallay.util.Java8Collections.setOf(), ModelEvent.ResponseStarted.class)); break $oaSwitch0_exit;
}
case "rate_limited":
{
$oaSwitch0_exit_result = new AgentEvent.ModelProgress(
                    read(body, dev.openallay.util.Java8Collections.setOf("retryAfterMillis", "attempt"), ModelEvent.RateLimited.class)); break $oaSwitch0_exit;
}
case "model_complete":
{
$oaSwitch0_exit_result = new AgentEvent.ModelProgress(
                    read(body, dev.openallay.util.Java8Collections.setOf("stopReason"), ModelEvent.MessageComplete.class)); break $oaSwitch0_exit;
}
case "model_failure":
{
$oaSwitch0_exit_result = new AgentEvent.ModelProgress(readModelFailure(body)); break $oaSwitch0_exit;
}
case "tool_started":
{
$oaSwitch0_exit_result = readToolStarted(body); break $oaSwitch0_exit;
}
case "tool_completed":
{
$oaSwitch0_exit_result = read(
                    body,
                    dev.openallay.util.Java8Collections.setOf("invocationId", "toolId", "failure", "normalized"),
                    AgentEvent.ToolCompleted.class); break $oaSwitch0_exit;
}
case "final_text":
{
$oaSwitch0_exit_result = read(body, dev.openallay.util.Java8Collections.setOf("text"), AgentEvent.FinalText.class); break $oaSwitch0_exit;
}
case "failed":
{
$oaSwitch0_exit_result = read(body, dev.openallay.util.Java8Collections.setOf("code", "message"), AgentEvent.Failed.class); break $oaSwitch0_exit;
}
default:
{
throw new IllegalArgumentException(
                    "Unknown server Agent event type " + payload.eventType());
}
}
}
AgentEvent event = $oaSwitch0_exit_result;
        boolean expectedTerminal = event instanceof AgentEvent.FinalText || event instanceof AgentEvent.Failed;
        if (payload.terminal() != expectedTerminal) {
            throw new IllegalArgumentException("Server Agent event terminal flag is inconsistent");
        }
        return event;
    }

    private AgentEvent.ModelUsageStarted readUsageStarted(JsonObject body) {
        if (!dev.openallay.json.JsonTrees.keys(body).equals(dev.openallay.util.Java8Collections.setOf("callId", "modelIdentifier"))
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
        if (!dev.openallay.json.JsonTrees.keys(body).equals(dev.openallay.util.Java8Collections.setOf("callId", "modelIdentifier", "usage"))
                || !body.get("callId").isJsonPrimitive()
                || !body.getAsJsonPrimitive("callId").isString()
                || !body.get("modelIdentifier").isJsonPrimitive()
                || !body.getAsJsonPrimitive("modelIdentifier").isString()
                || !body.get("usage").isJsonObject()) {
            throw new IllegalArgumentException("Server model usage receipt schema mismatch");
        }
        JsonObject usage = body.getAsJsonObject("usage");
        Set<String> counts = dev.openallay.util.Java8Collections.setOf("inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens", "uncachedInputTokens");
        Set<String> known = dev.openallay.util.Java8Collections.setOf("inputKnown", "outputKnown", "cacheReadKnown", "cacheWriteKnown", "uncachedInputKnown");
        java.util.HashSet<String> fields = new java.util.HashSet<>(counts);
        fields.addAll(known);
        if (!dev.openallay.json.JsonTrees.keys(usage).equals(fields)) {
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
        UUID messageId = readMessageId(body, dev.openallay.util.Java8Collections.setOf("messageId", "message"));
        BridgeJsonCodec.validateHistoryMessage(body.get("message"));
        ServerAgentHistoryMessage message = gson.fromJson(body.get("message"), ServerAgentHistoryMessage.class);
        ServerAgentSteerPayload.validateMessage(message);
        return new AgentEvent.SteerApplied(messageId, message.toModelMessage());
    }

    private UUID readMessageId(JsonObject body, Set<String> fields) {
        if (!dev.openallay.json.JsonTrees.keys(body).equals(fields) || body.get("messageId") == null
                || !body.get("messageId").isJsonPrimitive()
                || !body.getAsJsonPrimitive("messageId").isString()) {
            throw new IllegalArgumentException("Server steer event schema mismatch");
        }
        return UUID.fromString(body.get("messageId").getAsString());
    }

    private AgentEvent.ContextUpdated readContext(JsonObject body) {
        if (!dev.openallay.json.JsonTrees.keys(body).equals(dev.openallay.util.Java8Collections.setOf("messages", "requestMessages"))) {
            throw new IllegalArgumentException("Server model context schema mismatch");
        }
        return new AgentEvent.ContextUpdated(contexts.decode(body.get("messages").toString()),
                contexts.decode(body.get("requestMessages").toString()));
    }

    private AgentEvent.ContextFinalized readFinalized(JsonObject body) {
        if (!dev.openallay.json.JsonTrees.keys(body).equals(dev.openallay.util.Java8Collections.setOf("messages", "requestMessages"))) {
            throw new IllegalArgumentException("Server finalized context schema mismatch");
        }
        return new AgentEvent.ContextFinalized(contexts.decode(body.get("messages").toString()),
                contexts.decode(body.get("requestMessages").toString()));
    }

    private <T> T read(JsonObject body, Set<String> fields, Class<T> type) {
        if (!dev.openallay.json.JsonTrees.keys(body).equals(fields)) {
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
        Set<String> fields = dev.openallay.json.JsonTrees.keys(body);
        if (!fields.equals(dev.openallay.util.Java8Collections.setOf("code", "message"))
                && !fields.equals(dev.openallay.util.Java8Collections.setOf("code", "message", "httpStatus"))) {
            throw new IllegalArgumentException("Server Agent event schema mismatch for ModelFailure");
        }
        return gson.fromJson(body, ModelFailure.class);
    }

    private ModelEvent.AttemptStarted readAttemptStarted(JsonObject body) {
        if (!dev.openallay.json.JsonTrees.keys(body).equals(dev.openallay.util.Java8Collections.setOf("attempt"))
                && !dev.openallay.json.JsonTrees.keys(body).equals(dev.openallay.util.Java8Collections.setOf("attempt", "attemptTimeoutMillis"))) {
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
        if (!dev.openallay.json.JsonTrees.keys(body).equals(dev.openallay.util.Java8Collections.setOf("invocationId", "toolId", "presentationMessages"))) {
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
        final class $oaPattern6_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ModelProgress bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ModelProgress && (($oaPattern6_holder.bound = (AgentEvent.ModelProgress) $oaPattern6_holder.value) != null))) {
            ModelEvent modelEvent = Objects.requireNonNull($oaPattern6_holder.bound.event());
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
