package dev.openallay.model.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.agent.AgentRequest;
import dev.openallay.agent.AgentState;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.agent.trace.LiveAgentTraceRecorder;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.image.ImageReference;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RetainedModelRequestDiagnosticTest {
    @Test
    void reconstructsExactLastRequestIncludingToolHistoryWithoutExecutingTools() {
        JsonObject schema = JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject();
        JsonObject input = JsonParser.parseString("""
                {"value":7,"reasoning":"ordinary tool data","signature":"ordinary tool signature"}
                """).getAsJsonObject();
        List<ModelMessage> messages = List.of(
                ModelMessage.userText("Ask"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.Reasoning("private reasoning", "private signature"),
                        new ModelContent.Text("Visible plan"),
                        new ModelContent.ToolUse("call_1", "fact", input))),
                new ModelMessage(ModelRole.USER, List.of(
                        new ModelContent.ToolResult("call_1", input, false))));
        ModelRequest request = new ModelRequest("System instruction", ModelContextCodec.safe(messages),
                List.of(new ModelToolDefinition("fact", "Get fact", schema)), true, "retained", 192);
        ModelRequest earlier = new ModelRequest("Earlier instruction", List.of(ModelMessage.userText("Earlier")),
                List.of(), false, "earlier");
        JsonObject trace = trace(earlier, request);
        JsonObject payload = trace.getAsJsonArray("events").get(2).getAsJsonObject().getAsJsonObject("payload");

        ModelRequest reconstructed = LiveModelContinuationDiagnosticTest.retainedRequest(trace);

        assertEquals(request.systemPrompt(), reconstructed.systemPrompt());
        assertEquals(request.messages(), reconstructed.messages());
        assertEquals(request.tools(), reconstructed.tools());
        assertEquals(request.stream(), reconstructed.stream());
        assertEquals(request.sessionKey(), reconstructed.sessionKey());
        assertEquals(request.maxOutputTokens(), reconstructed.maxOutputTokens());
        assertEquals(payload, modelFacingPayload(reconstructed));
        assertFalse(payload.has("images"));
        assertFalse(payload.toString().contains("private reasoning"));
        assertFalse(payload.toString().contains("private signature"));
        assertEquals(new ModelContent.Text("Visible plan"), reconstructed.messages().get(1).content().getFirst());
        assertEquals(input, ((ModelContent.ToolUse) reconstructed.messages().get(1).content().get(1)).input());
        assertEquals(input, ((ModelContent.ToolResult) reconstructed.messages().get(2).content().getFirst()).value());
        assertEquals(new ModelContent.Reasoning("private reasoning", "private signature"),
                messages.get(1).content().getFirst(), "safe trace preparation must not mutate live messages");
    }

    @Test
    void rejectsUnknownRetainedContentRatherThanSilentlyDroppingIt() {
        JsonObject trace = JsonParser.parseString("""
                {"events":[{"type":"model_request","payload":{"systemPrompt":"system",
                "messages":[{"role":"USER","content":[{"unknown":"value"}]}],
                "tools":[],"stream":true,"sessionKey":"retained"}}]}
                """).getAsJsonObject();
        assertThrows(IllegalArgumentException.class,
                () -> LiveModelContinuationDiagnosticTest.retainedRequest(trace));
    }

    @Test
    void rejectsPersistedResolverAndUnknownModelFacingFields() {
        ModelRequest request = new ModelRequest("System instruction", List.of(ModelMessage.userText("Ask")),
                List.of(), false, "retained");
        for (String field : List.of("images", "unknown")) {
            JsonObject trace = trace(request);
            trace.getAsJsonArray("events").get(1).getAsJsonObject().getAsJsonObject("payload")
                    .add(field, new JsonObject());
            assertThrows(IllegalArgumentException.class,
                    () -> LiveModelContinuationDiagnosticTest.retainedRequest(trace), field);
        }
    }

    @Test
    void rejectsTypedPrivateReasoningRatherThanTreatingItAsVisibleText() {
        ModelRequest request = new ModelRequest("System instruction", List.of(
                ModelMessage.userText("Ask"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.Reasoning("private reasoning", "private signature")))),
                List.of(), false, "retained");
        assertThrows(IllegalArgumentException.class,
                () -> LiveModelContinuationDiagnosticTest.retainedRequest(trace(request)));
    }

    @Test
    void reconstructsTypedImageMetadataWithoutPersistingOrReadingTheResolver() {
        ImageReference reference = new ImageReference("a".repeat(64), "image/png", 2, 3, 96);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        ModelRequest request = new ModelRequest("System instruction", List.of(
                ModelMessage.userInput("Look", List.of(reference))), List.of(), false, "retained", null,
                image -> {
                    reads.incrementAndGet();
                    throw new IOException("fixture resolver must never be read");
                });
        JsonObject trace = trace(request);
        JsonObject payload = trace.getAsJsonArray("events").get(1).getAsJsonObject().getAsJsonObject("payload");

        ModelRequest reconstructed = LiveModelContinuationDiagnosticTest.retainedRequest(trace);

        assertEquals(request.messages(), reconstructed.messages());
        assertEquals(new ModelContent.Image(reference), reconstructed.messages().getFirst().content().get(1));
        assertEquals(modelFacingPayload(request), payload);
        assertEquals(payload, modelFacingPayload(reconstructed));
        assertFalse(payload.has("images"));
        assertEquals(0, reads.get(), "recording and reconstruction must not read a scoped resolver");
        IOException unavailable = assertThrows(IOException.class, () -> reconstructed.images().read(reference));
        assertTrue(unavailable.getMessage().contains("unavailable"));
        assertEquals(0, reads.get(), "reconstruction must use an unavailable resolver, not the original one");
    }

    @Test
    void rejectsUnknownImageReferenceAndContentFields() {
        ImageReference reference = new ImageReference("a".repeat(64), "image/png", 2, 3, 96);
        ModelRequest request = new ModelRequest("System instruction", List.of(
                ModelMessage.userInput(null, List.of(reference))), List.of(), false, "retained");
        for (boolean nested : List.of(false, true)) {
            JsonObject trace = trace(request);
            JsonObject content = trace.getAsJsonArray("events").get(1).getAsJsonObject().getAsJsonObject("payload")
                    .getAsJsonArray("messages").get(0).getAsJsonObject().getAsJsonArray("content")
                    .get(0).getAsJsonObject();
            JsonObject target = nested ? content.getAsJsonObject("reference") : content;
            target.addProperty("unknown", "unexpected");
            assertThrows(IllegalArgumentException.class,
                    () -> LiveModelContinuationDiagnosticTest.retainedRequest(trace));
        }
    }

    private static JsonObject trace(ModelRequest... requests) {
        AgentRequest agentRequest = new AgentRequest(UUID.randomUUID(), UUID.randomUUID(), "retained", "Ask",
                "System instruction", ToolInvocationContext.developmentConsole("retained-request-test"), false);
        LiveAgentTraceRecorder recorder = new LiveAgentTraceRecorder(new Gson(), agentRequest);
        for (ModelRequest request : requests) {
            recorder.modelRequest(request);
        }
        recorder.state(AgentState.COMPLETED);
        return new Gson().toJsonTree(recorder.finish(AgentState.COMPLETED, "Done", null)).getAsJsonObject();
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
}
