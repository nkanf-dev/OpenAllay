package dev.openallay.model.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelToolDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RetainedModelRequestDiagnosticTest {
    @Test
    void reconstructsExactLastRequestIncludingToolHistoryWithoutExecutingTools() {
        JsonObject schema = JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject();
        JsonObject input = JsonParser.parseString("{\"value\":7}").getAsJsonObject();
        ModelRequest request = new ModelRequest("System instruction", List.of(
                ModelMessage.userText("Ask"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.Reasoning("reason", "signature"),
                        new ModelContent.ToolUse("call_1", "fact", input))),
                new ModelMessage(ModelRole.USER, List.of(
                        new ModelContent.ToolResult("call_1", input, false)))),
                List.of(new ModelToolDefinition("fact", "Get fact", schema)), true, "retained");
        JsonObject trace = new JsonObject();
        JsonArray events = new JsonArray();
        JsonObject event = new JsonObject();
        event.addProperty("type", "model_request");
        event.add("payload", new Gson().toJsonTree(request));
        events.add(event);
        trace.add("events", events);

        ModelRequest reconstructed = LiveModelContinuationDiagnosticTest.retainedRequest(trace);

        assertEquals(request, reconstructed);
        assertEquals(event.get("payload"), new Gson().toJsonTree(reconstructed));
    }

    @Test
    void rejectsUnknownRetainedContentRatherThanSilentlyDroppingIt() {
        JsonObject trace = JsonParser.parseString("""
                {"events":[{"type":"model_request","payload":{"systemPrompt":"system",
                "messages":[{"role":"USER","content":[{"unknown":"value"}]}],
                "tools":[],"stream":true,"sessionKey":"retained"}}]}
                """).getAsJsonObject();
        assertThrows(RuntimeException.class,
                () -> LiveModelContinuationDiagnosticTest.retainedRequest(trace));
    }
}
