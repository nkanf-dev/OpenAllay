package dev.openallay.bridge.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelUsage;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class ServerAgentEventCodecTest {
    private final ServerAgentEventCodec codec = new ServerAgentEventCodec(dev.openallay.json.EngineJson.create());

    @Test
    void actualCallStartRoundTripsStrictIdentityOnly() {
        UUID request = UUID.randomUUID();
        AgentEvent.ModelUsageStarted original = new AgentEvent.ModelUsageStarted(UUID.randomUUID(), "model");
        ServerAgentEventPayload encoded = codec.encode(request, original);
        assertEquals("model_usage_started", encoded.eventType());
        assertEquals(false, encoded.terminal());
        assertEquals(original, codec.decode(encoded, request));
        JsonObject body = dev.openallay.json.JsonTrees.parse(encoded.eventJson()).getAsJsonObject();
        assertEquals(Set.of("callId", "modelIdentifier"), dev.openallay.json.JsonTrees.keys(body));
        body.addProperty("unknown", true);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new ServerAgentEventPayload(
                request, encoded.eventType(), body.toString(), false), request));
    }

    @Test
    void actualCallReceiptRoundTripsCountsOnlyWithStrictKnownBitsAndRequestIdentity() {
        UUID request = UUID.randomUUID();
        AgentEvent.ModelUsageObserved original = new AgentEvent.ModelUsageObserved(
                UUID.randomUUID(), "actual-model", ModelUsage.openAi(12, true, 0, false, 0, false));
        ServerAgentEventPayload encoded = codec.encode(request, original);
        assertEquals("model_usage_observed", encoded.eventType());
        assertEquals(false, encoded.terminal());
        assertEquals(original, codec.decode(encoded, request));
        JsonObject body = dev.openallay.json.JsonTrees.parse(encoded.eventJson()).getAsJsonObject();
        assertEquals(Set.of("callId", "modelIdentifier", "usage"), dev.openallay.json.JsonTrees.keys(body));
        assertEquals(Set.of("inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens",
                "uncachedInputTokens", "inputKnown", "outputKnown", "cacheReadKnown",
                "cacheWriteKnown", "uncachedInputKnown"), dev.openallay.json.JsonTrees.keys(body.getAsJsonObject("usage")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new ServerAgentEventPayload(
                request, encoded.eventType(), encoded.eventJson(), true), request));
        for (String field : List.copyOf(dev.openallay.json.JsonTrees.keys(body.getAsJsonObject("usage")))) {
            JsonObject missing = dev.openallay.json.JsonTrees.copy(body);
            missing.getAsJsonObject("usage").remove(field);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(new ServerAgentEventPayload(
                    request, encoded.eventType(), missing.toString(), false), request));
        }
        for (String bad : List.of("\"12\"", "1.5", "-1", "9223372036854775808", "null")) {
            JsonObject invalid = dev.openallay.json.JsonTrees.copy(body);
            invalid.getAsJsonObject("usage").add("inputTokens", dev.openallay.json.JsonTrees.parse(bad));
            assertThrows(IllegalArgumentException.class, () -> codec.decode(new ServerAgentEventPayload(
                    request, encoded.eventType(), invalid.toString(), false), request));
        }
        JsonObject invalidKnown = dev.openallay.json.JsonTrees.copy(body);
        invalidKnown.getAsJsonObject("usage").addProperty("inputKnown", "true");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new ServerAgentEventPayload(
                request, encoded.eventType(), invalidKnown.toString(), false), request));
        JsonObject extra = dev.openallay.json.JsonTrees.copy(body);
        extra.addProperty("providerBody", "not permitted");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new ServerAgentEventPayload(
                request, encoded.eventType(), extra.toString(), false), request));
    }

    @Test
    void truthfulContextRoundTripsOriginalProgramsPlaintextErrorsAndStrictShape() {
        UUID request = UUID.randomUUID();
        JsonObject input = new JsonObject();
        input.addProperty("source", "return mc.items.filter(x => x.id); ");
        List<dev.openallay.model.ModelMessage> messages = List.of(
                dev.openallay.model.ModelMessage.userText("Compare items"),
                new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                        List.of(new dev.openallay.model.ModelContent.ToolUse(
                                "actual-call", "openallay__run_javascript", input))),
                new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.USER,
                        List.of(new dev.openallay.model.ModelContent.ToolResult("actual-call",
                                new com.google.gson.JsonPrimitive("status: failure\ncode: javascript_error\nmessage: .filter is undefined"), true))));
        AgentEvent.ContextUpdated original = new AgentEvent.ContextUpdated(messages, messages);
        ServerAgentEventPayload encoded = codec.encode(request, original);
        assertEquals("context_updated", encoded.eventType());
        assertEquals(false, encoded.terminal());
        assertEquals(original, codec.decode(encoded, request));
        AgentEvent.ContextFinalized finalized = new AgentEvent.ContextFinalized(messages, messages);
        var finalizedPayload = codec.encode(request, finalized);
        assertEquals("context_finalized", finalizedPayload.eventType());
        assertEquals(false, finalizedPayload.terminal());
        assertEquals(finalized, codec.decode(finalizedPayload, request));
        org.junit.jupiter.api.Assertions.assertFalse(encoded.eventJson().contains("durableProjection"));
        JsonObject malformed = dev.openallay.json.JsonTrees.parse(encoded.eventJson()).getAsJsonObject();
        malformed.addProperty("unknown", true);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new ServerAgentEventPayload(
                request, encoded.eventType(), malformed.toString(), false), request));
    }

    @Test
    void roundTripsObservableEventsAndTerminalFlags() {
        UUID request = UUID.randomUUID();
        assertEquals(
                "hello",
                assertInstanceOf(
                                ModelEvent.TextDelta.class,
                                assertInstanceOf(
                                                AgentEvent.ModelProgress.class,
                                                codec.decode(codec.encode(
                                                        request,
                                                        new AgentEvent.ModelProgress(
                                                                new ModelEvent.TextDelta("hello"))),
                                                        request))
                                        .event())
                        .text());
        assertEquals(
                new ModelUsage(4, 2, 1),
                assertInstanceOf(
                                ModelEvent.UsageUpdate.class,
                                assertInstanceOf(
                                                AgentEvent.ModelProgress.class,
                                                codec.decode(codec.encode(
                                                        request,
                                                        new AgentEvent.ModelProgress(
                                                                new ModelEvent.UsageUpdate(
                                                                        new ModelUsage(4, 2, 1)))),
                                                        request))
                                        .event())
                        .usage());
        ServerAgentEventPayload encodedAttempt = codec.encode(
                request,
                new AgentEvent.ModelProgress(
                        new ModelEvent.AttemptStarted(2, 12_345L)));
        assertEquals(
                Set.of("attempt", "attemptTimeoutMillis"),
                dev.openallay.json.JsonTrees.keys(dev.openallay.json.JsonTrees.parse(encodedAttempt.eventJson())
                        .getAsJsonObject()
                        ));
        ModelEvent.AttemptStarted attempt = assertInstanceOf(
                ModelEvent.AttemptStarted.class,
                assertInstanceOf(
                                AgentEvent.ModelProgress.class,
                                codec.decode(encodedAttempt, request))
                        .event());
        assertEquals(2, attempt.attempt());
        assertEquals(12_345L, attempt.attemptTimeoutMillis());
        assertInstanceOf(
                ModelEvent.ResponseStarted.class,
                assertInstanceOf(
                                AgentEvent.ModelProgress.class,
                                codec.decode(codec.encode(
                                        request,
                                        new AgentEvent.ModelProgress(
                                                new ModelEvent.ResponseStarted())),
                                        request))
                        .event());

        AgentEvent.ToolStarted started = assertInstanceOf(
                AgentEvent.ToolStarted.class,
                codec.decode(
                        codec.encode(request, new AgentEvent.ToolStarted(
                                "call-1",
                                "openallay:get_recipe",
                                List.of(GuideToolMessage.of(
                                        GuideToolMessage.Key.INVOCATION_LOAD_SKILL_EXACT,
                                        "minecraft:iron_block")))),
                        request));
        assertEquals("call-1", started.invocationId());
        assertEquals(
                List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.INVOCATION_LOAD_SKILL_EXACT,
                        "minecraft:iron_block")),
                started.presentationMessages());

        AgentEvent.ToolCompleted completed = assertInstanceOf(
                AgentEvent.ToolCompleted.class,
                codec.decode(
                        codec.encode(request, new AgentEvent.ToolCompleted(
                                "call-1",
                                "openallay:get_recipe",
                                false,
                                new JsonObject())),
                        request));
        assertEquals("call-1", completed.invocationId());

        ContextCheckpoint checkpoint = new ContextCheckpoint(
                UUID.randomUUID(), 0, 2, "a".repeat(64), "model",
                Instant.EPOCH, ContextCheckpoint.Status.SUCCEEDED, "{}", null, null, 42);
        AgentEvent.ContextCompacted compacted = assertInstanceOf(
                AgentEvent.ContextCompacted.class,
                codec.decode(codec.encode(
                        request, new AgentEvent.ContextCompacted(checkpoint)), request));
        assertEquals(checkpoint, compacted.checkpoint());

        ServerAgentEventPayload terminal =
                codec.encode(request, new AgentEvent.FinalText("done"));
        assertEquals(true, terminal.terminal());
        assertEquals("done", assertInstanceOf(
                AgentEvent.FinalText.class, codec.decode(terminal, request)).text());
    }

    @Test
    void serverPendingCardsReceiveIntentWithoutRawArgumentsOrNewWireFields() {
        UUID request = UUID.randomUUID();
        JsonObject input = new JsonObject();
        input.addProperty("source", "private source must not cross ToolStarted");
        input.addProperty("title", "比较武器");
        input.addProperty("description", "按攻击伤害排列");
        ServerAgentEventPayload encoded = codec.encode(request, new AgentEvent.ToolStarted(
                "call-intent", "openallay:run_javascript", input,
                dev.openallay.guide.GuideToolInvocationPresentation.messages("openallay:run_javascript", input)));
        assertEquals(Set.of("invocationId", "toolId", "presentationMessages"),
                dev.openallay.json.JsonTrees.keys(dev.openallay.json.JsonTrees.parse(encoded.eventJson()).getAsJsonObject()));
        org.junit.jupiter.api.Assertions.assertFalse(encoded.eventJson().contains("private source"));
        AgentEvent.ToolStarted decoded = assertInstanceOf(AgentEvent.ToolStarted.class, codec.decode(encoded, request));
        var snapshot = dev.openallay.guide.GuideRequestSnapshot.start(request, "main",
                dev.openallay.guide.GuideTopology.SERVER, "Compare", Instant.EPOCH);
        snapshot = new dev.openallay.guide.GuideStateReducer(dev.openallay.json.EngineJson.create()).apply(snapshot, decoded, Instant.EPOCH);
        assertEquals("call-intent", snapshot.tools().getFirst().invocationId());
        assertEquals(dev.openallay.guide.GuideToolStatus.RUNNING, snapshot.tools().getFirst().status());
        assertEquals(new dev.openallay.guide.GuideToolIntent("比较武器", "按攻击伤害排列"),
                snapshot.tools().getFirst().intent());
    }

    @Test
    void rejectsUnknownMismatchedAndInconsistentEvents() {
        UUID request = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                new ServerAgentEventPayload(
                        request, "future_event", "{}", false),
                request));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                new ServerAgentEventPayload(
                        request,
                        "model_attempt_started",
                        "{\"attempt\":1,\"provider\":\"secret\"}",
                        false),
                request));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                new ServerAgentEventPayload(
                        request,
                        "model_attempt_started",
                        "{\"attempt\":1,\"deadlineEpochMillis\":12345}",
                        false),
                request));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                new ServerAgentEventPayload(
                        request, "context_compacted", "{}", false),
                request));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                new ServerAgentEventPayload(
                        request,
                        "tool_started",
                        "{\"toolId\":\"openallay:get_recipe\"}",
                        false),
                request));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                new ServerAgentEventPayload(
                        request,
                        "tool_started",
                        "{\"invocationId\":\"call-1\",\"toolId\":\"openallay:get_recipe\","
                                + "\"presentationMessages\":[{\"key\":\"RESULT_COMPLETED\","
                                + "\"arguments\":[],\"arbitraryTranslationKey\":\"evil.key\"}]}",
                        false),
                request));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                codec.encode(request, new AgentEvent.FinalText("done")), UUID.randomUUID()));
        ServerAgentEventPayload finalText = codec.encode(request, new AgentEvent.FinalText("done"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                new ServerAgentEventPayload(
                        finalText.requestId(),
                        finalText.eventType(),
                        finalText.eventJson(),
                        false),
                request));
    }

    @Test
    void bridgeCodecAcceptsOnlyRequestCorrelatedCancelShape() {
        UUID request = UUID.randomUUID();
        BridgeJsonCodec json = new BridgeJsonCodec();
        ServerAgentCancelPayload value = new ServerAgentCancelPayload(request);
        assertEquals(Set.of("requestId"),
                dev.openallay.json.JsonTrees.keys(dev.openallay.json.JsonTrees.parse(json.encode(value)).getAsJsonObject()));
        assertEquals(request, json.decode(
                json.encode(value), ServerAgentCancelPayload.class).requestId());
        assertThrows(IllegalArgumentException.class,
                () -> json.decode("{}", ServerAgentCancelPayload.class));
        JsonObject extraField = dev.openallay.json.JsonTrees.parse(json.encode(value)).getAsJsonObject();
        extraField.addProperty("eventId", UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class,
                () -> json.decode(extraField.toString(), ServerAgentCancelPayload.class));
    }
}
