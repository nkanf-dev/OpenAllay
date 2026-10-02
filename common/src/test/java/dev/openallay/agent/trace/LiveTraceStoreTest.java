package dev.openallay.agent.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.agent.AgentRequest;
import dev.openallay.agent.AgentState;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.image.ImageReference;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LiveTraceStoreTest {
    @TempDir java.nio.file.Path temporary;

    @Test
    void preservesFullTraceAndCredentialNamedPlayerAndToolData() throws Exception {
        String question = "完整问题，不截断\n".repeat(4096) + "token=one-time-test-key";
        String source = "const password = 'synthetic-password';\n".repeat(4096)
                + "return {authorization: 'Bearer header-only-secret', count: 2};";
        JsonObject nested = new JsonObject();
        nested.addProperty("token", "one-time-test-key");
        nested.addProperty("password", "synthetic-password");
        nested.addProperty("authorization", "Bearer header-only-secret");
        nested.addProperty("api_key", "sk-" + "syntheticabc123456789");
        nested.addProperty("x-api-key", "pk-syntheticabc123456789");
        nested.addProperty("Cookie", "first=private; second=hidden");
        nested.addProperty("endpoint", "https://user:pass@example.invalid/path?token=test#entry");
        nested.addProperty("errorText", "TypeError: Authorization: Digest secret=private, nonce=hidden\n"
                + "at guide.js:8: filter is undefined");
        nested.addProperty("reasoning", "ordinary player-provided tool field");
        nested.addProperty("signature", "ordinary player-provided tool signature");
        nested.addProperty("count", 2);
        nested.addProperty("complete", false);
        JsonArray rows = new JsonArray();
        rows.add(nested);
        rows.add("preserve-unrelated-text");
        JsonObject payload = new JsonObject();
        payload.addProperty("question", question);
        payload.addProperty("source", source);
        payload.add("nested", rows);
        JsonObject originalPayload = payload.deepCopy();
        LiveAgentTrace trace = trace(payload);
        LiveTraceStore store = new LiveTraceStore(temporary);

        store.record(trace);
        String encoded = store.encoded(trace.requestId());
        JsonObject encodedTrace = JsonParser.parseString(encoded).getAsJsonObject();
        JsonObject encodedPayload = encodedTrace.getAsJsonArray("events").get(0).getAsJsonObject()
                .getAsJsonObject("payload");
        assertEquals(originalPayload, encodedPayload);
        assertEquals(question, encodedPayload.get("question").getAsString());
        assertEquals(source, encodedPayload.get("source").getAsString());
        assertEquals(rows, encodedPayload.getAsJsonArray("nested"));
        assertEquals("request", encodedTrace.getAsJsonArray("events").get(0).getAsJsonObject()
                .get("type").getAsString());
        assertEquals(1, encodedTrace.getAsJsonArray("events").get(0).getAsJsonObject()
                .get("elapsedNanos").getAsLong());
        assertEquals(trace.requestId().toString(), encodedTrace.get("requestId").getAsString());
        assertEquals("COMPLETED", encodedTrace.get("finalState").getAsString());
        assertEquals("final text 不截断\n".repeat(4096), encodedTrace.get("finalText").getAsString());
        assertEquals(trace, store.find(trace.requestId()).orElseThrow());
        assertEquals(originalPayload, payload, "recording and encoding must not mutate the source payload");
        assertEquals(originalPayload, trace.events().getFirst().payload());
        var path = temporary.resolve(trace.requestId() + ".json");
        assertTrue(Files.exists(path));
        assertEquals(encodedTrace, JsonParser.parseString(Files.readString(path)));
    }

    @Test
    void persistenceFollowsDebugModeAndPreservesOriginalPayload() throws Exception {
        String secret = "configured-profile-secret";
        java.util.concurrent.atomic.AtomicBoolean debug = new java.util.concurrent.atomic.AtomicBoolean();
        LiveTraceStore store = new LiveTraceStore(temporary, debug::get);
        LiveAgentTrace offTrace = trace(new JsonObject());
        store.record(offTrace);
        assertFalse(Files.exists(temporary.resolve(offTrace.requestId() + ".json")));

        debug.set(true);
        JsonObject payload = new JsonObject();
        payload.addProperty("provider", "cookie=" + secret);
        payload.addProperty("authorization", "Bearer header-only-secret");
        payload.addProperty("cookieHeader", "session=header-only-cookie");
        payload.addProperty("source", "return 'full javascript source';");
        LiveAgentTrace onTrace = trace(payload);
        store.record(onTrace);
        var path = temporary.resolve(onTrace.requestId() + ".json");
        assertTrue(Files.exists(path));
        JsonObject persisted = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(payload, persisted.getAsJsonArray("events").get(0).getAsJsonObject().get("payload"));
        assertEquals(JsonParser.parseString(store.encoded(onTrace.requestId())), persisted);
        assertEquals("return 'full javascript source';", payload.get("source").getAsString());
        assertEquals("cookie=" + secret, payload.get("provider").getAsString());
        assertEquals("Bearer header-only-secret", payload.get("authorization").getAsString());
        assertEquals("session=header-only-cookie", payload.get("cookieHeader").getAsString());

        debug.set(false);
        LiveAgentTrace toggledOff = trace(new JsonObject());
        store.record(toggledOff);
        assertFalse(Files.exists(temporary.resolve(toggledOff.requestId() + ".json")));
    }

    @Test
    void disabledPersistenceWritesNothingAndLeavesRecordedTraceAvailable() throws Exception {
        LiveTraceStore store = new LiveTraceStore(null);
        LiveAgentTrace trace = trace(new JsonObject());
        store.record(trace);

        assertEquals(trace, store.find(trace.requestId()).orElseThrow());
        try (var files = Files.list(temporary)) {
            assertTrue(files.findAny().isEmpty());
        }
    }

    @Test
    void recorderExcludesTypedTurnReasoningAndPreservesSafeRequestAndOrdinaryReasoningFields() throws Exception {
        Gson gson = new Gson();
        ImageReference reference = new ImageReference("a".repeat(64), "image/png", 2, 3, 96);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        String resolverState = "resolver-only-binary-state";
        dev.openallay.model.image.ImagePayloadResolver resolver = image -> {
            reads.incrementAndGet();
            throw new IOException(resolverState);
        };
        ModelMessage userInput = ModelMessage.userInput("player token=synthetic-player-token", List.of(reference));
        AgentRequest request = new AgentRequest(UUID.randomUUID(), UUID.randomUUID(), "main",
                userInput, resolver, "system prompt",
                ToolInvocationContext.developmentConsole("trace-private-reasoning"), false);
        LiveAgentTraceRecorder recorder = new LiveAgentTraceRecorder(gson, request);
        JsonObject input = new JsonObject();
        input.addProperty("source", "return {reasoning: 'ordinary-tool-data'};");
        input.addProperty("reasoning", "ordinary-tool-reasoning");
        input.addProperty("signature", "ordinary-tool-signature");
        ModelContent.ToolUse use = new ModelContent.ToolUse("original_call", "openallay__run_javascript", input);
        ModelContent.ToolResult result = new ModelContent.ToolResult("original_call", input, false);
        ModelMessage reasoningOnly = new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.Reasoning("request-only-private-thought", "request-only-private-signature")));
        List<ModelMessage> messages = List.of(
                userInput,
                reasoningOnly,
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.Reasoning("request-private-thought", "request-private-signature"), use)),
                new ModelMessage(ModelRole.USER, List.of(result)));
        List<ModelContent> turnContent = List.of(
                new ModelContent.Reasoning("turn-private-thought", "turn-private-signature"),
                new ModelContent.Text("visible answer token=synthetic-output-token"), use);
        ModelToolDefinition tool = new ModelToolDefinition("openallay__run_javascript", "Run source",
                JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject());
        ModelRequest modelRequest = new ModelRequest("system prompt", ModelContextCodec.safe(messages),
                List.of(tool), false, "main", 192, resolver);
        ModelTurn modelTurn = new ModelTurn("provider", "model", turnContent, "tool_use", ModelUsage.empty());
        recorder.modelRequest(modelRequest);
        recorder.modelTurn(modelTurn);
        LiveAgentTrace trace = recorder.finish(AgentState.COMPLETED, "visible final answer", null);
        LiveTraceStore store = new LiveTraceStore(temporary);

        store.record(trace);
        String encoded = store.encoded(trace.requestId());

        for (String privateValue : List.of("request-only-private-thought", "request-only-private-signature",
                "request-private-thought", "request-private-signature", "turn-private-thought",
                "turn-private-signature")) {
            assertFalse(encoded.contains(privateValue), privateValue);
        }
        JsonArray events = JsonParser.parseString(encoded).getAsJsonObject().getAsJsonArray("events");
        assertEquals(3, events.size());
        assertEquals(request.userMessage(), events.get(0).getAsJsonObject().getAsJsonObject("payload")
                .get("userMessage").getAsString());
        JsonObject encodedRequest = events.get(1).getAsJsonObject().getAsJsonObject("payload");
        JsonObject expectedRequest = modelFacingPayload(modelRequest);
        assertEquals(expectedRequest, encodedRequest,
                "the recorder must preserve every model-facing field without the request-only resolver");
        assertEquals(Set.of("systemPrompt", "messages", "tools", "stream", "sessionKey", "maxOutputTokens"),
                encodedRequest.keySet());
        JsonObject encodedReference = encodedRequest.getAsJsonArray("messages").get(0).getAsJsonObject()
                .getAsJsonArray("content").get(1).getAsJsonObject().getAsJsonObject("reference");
        JsonObject expectedReference = new JsonObject();
        expectedReference.addProperty("sha256", reference.sha256());
        expectedReference.addProperty("mimeType", reference.mimeType());
        expectedReference.addProperty("width", reference.width());
        expectedReference.addProperty("height", reference.height());
        expectedReference.addProperty("byteSize", reference.byteSize());
        assertEquals(expectedReference, encodedReference);
        assertEquals(Set.of("sha256", "mimeType", "width", "height", "byteSize"), encodedReference.keySet());
        assertEquals(192, encodedRequest.get("maxOutputTokens").getAsInt());
        assertEquals(gson.toJsonTree(userInput), events.get(0).getAsJsonObject()
                .getAsJsonObject("payload").get("userInput"));
        assertFalse(encodedRequest.has("images"));
        assertFalse(encoded.contains(resolverState));
        assertFalse(encoded.contains("$$Lambda"));
        assertFalse(encoded.contains("base64"));
        assertFalse(encoded.contains("data:image/"));
        assertEquals(0, reads.get(), "trace recording and persistence must never read image bytes");
        List<ModelMessage> expectedMessages = List.of(
                userInput,
                new ModelMessage(ModelRole.ASSISTANT, List.of(use)),
                new ModelMessage(ModelRole.USER, List.of(result)));
        assertEquals(expectedMessages, modelRequest.messages());
        JsonObject encodedTurn = events.get(2).getAsJsonObject().getAsJsonObject("payload");
        ModelTurn expectedTurn = new ModelTurn("provider", "model", List.of(turnContent.get(1), use),
                "tool_use", ModelUsage.empty());
        assertEquals(gson.toJsonTree(expectedTurn), encodedTurn);
        assertEquals(input, encodedTurn.getAsJsonArray("content").get(1).getAsJsonObject().get("input"));
        assertEquals(List.of(new ModelContent.Reasoning("request-only-private-thought",
                "request-only-private-signature")), reasoningOnly.content());
        assertEquals(List.of(new ModelContent.Reasoning("request-private-thought",
                "request-private-signature"), use), messages.get(2).content(),
                "the safe boundary must not mutate the source messages");
        assertEquals(turnContent, modelTurn.content(), "recording must not mutate the live model turn");
        assertEquals(encoded, Files.readString(temporary.resolve(trace.requestId() + ".json")));
    }

    private static JsonObject modelFacingPayload(ModelRequest request) {
        Gson gson = new Gson();
        JsonObject payload = new JsonObject();
        payload.addProperty("systemPrompt", request.systemPrompt());
        payload.add("messages", gson.toJsonTree(request.messages()));
        payload.add("tools", gson.toJsonTree(request.tools()));
        payload.addProperty("stream", request.stream());
        payload.addProperty("sessionKey", request.sessionKey());
        if (request.maxOutputTokens() != null) {
            payload.addProperty("maxOutputTokens", request.maxOutputTokens());
        }
        return payload;
    }

    private static LiveAgentTrace trace(JsonObject payload) {
        Instant now = Instant.now();
        return new LiveAgentTrace(
                UUID.randomUUID(), UUID.randomUUID(), "main", now, now,
                AgentState.COMPLETED,
                List.of(new LiveTraceEvent("request", 1, payload)),
                "final text 不截断\n".repeat(4096), null);
    }
}
