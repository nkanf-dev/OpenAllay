package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.openallay.agent.context.ContextStructure;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelContextBoundaryTest {
    @Test
    void preservesPlayerFieldNamesAndValuesWithoutMutatingFacts() {
        String first = "player-token-alpha";
        String second = "player-token-beta";
        JsonObject raw = new JsonObject();
        raw.addProperty(first, "ordinary-a");
        raw.addProperty(second, "ordinary-b");
        raw.addProperty("[REDACTED]", "ordinary-c");
        raw.addProperty("token", first);
        raw.addProperty("password", "player-password");
        raw.addProperty("APIkey", "player-api-value");
        raw.addProperty("fact", 42);
        raw.addProperty("observed", first);
        List<ModelMessage> messages = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.ToolUse("call", "test__fact", raw))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("call", raw, false))));
        ModelContextCodec codec = new ModelContextCodec();
        List<ModelMessage> decoded = codec.decode(codec.encode(messages));
        JsonObject value = ((ModelContent.ToolResult) decoded.getLast().content().getFirst())
                .value().getAsJsonObject();
        assertEquals(messages, decoded);
        assertEquals(raw, value);
        assertEquals(raw.size(), value.size());
        assertEquals(42, value.get("fact").getAsInt());
        assertEquals("ordinary-c", value.get("[REDACTED]").getAsString());
        assertTrue(raw.has(first));
        assertTrue(raw.has(second));
    }

    @Test
    void preservesDistinctInvocationIdsAndExactCallResultPairing() {
        String first = "token=player-a";
        String second = "token=player-b";
        JsonObject input = new JsonObject();
        input.addProperty("password", "player-value");
        List<ModelMessage> messages = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.ToolUse(first, "test__fact", input),
                new ModelContent.ToolUse(second, "test__fact", input))),
                new ModelMessage(ModelRole.USER, List.of(
                        new ModelContent.ToolResult(first, input, false),
                        new ModelContent.ToolResult(second, input, true))));
        ModelContextCodec codec = new ModelContextCodec();
        List<ModelMessage> decoded = codec.decode(codec.encode(messages));
        assertEquals(messages, decoded);
        assertEquals(1, ContextStructure.units(decoded).size());
        ModelContent.ToolUse firstUse = (ModelContent.ToolUse) decoded.getFirst().content().getFirst();
        ModelContent.ToolUse secondUse = (ModelContent.ToolUse) decoded.getFirst().content().getLast();
        ModelContent.ToolResult firstResult = (ModelContent.ToolResult) decoded.getLast().content().getFirst();
        ModelContent.ToolResult secondResult = (ModelContent.ToolResult) decoded.getLast().content().getLast();
        assertEquals(first, firstUse.id());
        assertEquals(second, secondUse.id());
        assertNotEquals(firstUse.id(), secondUse.id());
        assertEquals(firstUse.id(), firstResult.toolUseId());
        assertEquals(secondUse.id(), secondResult.toolUseId());
        assertFalse(firstResult.error());
        assertTrue(secondResult.error());
    }

    @Test
    void retainsUnchangedImmutableContentIdentityAndForgetsPrivateReasoning() {
        JsonObject input = new JsonObject();
        input.addProperty("source", "return {token: 'player-value'};");
        ModelContent.ToolUse use = new ModelContent.ToolUse("call", "test__fact", input);
        ModelContent.ToolResult result = new ModelContent.ToolResult("call", input, false);
        List<ModelMessage> messages = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(use)),
                new ModelMessage(ModelRole.USER, List.of(result)));
        List<ModelMessage> safe = ModelContextCodec.safe(messages);
        assertSame(messages.getFirst(), safe.getFirst());
        assertSame(use, safe.getFirst().content().getFirst());
        assertSame(result, safe.getLast().content().getFirst());
        List<ModelMessage> privateContent = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.Reasoning("private", "private-signature"), new ModelContent.Text("ordinary"))));
        assertEquals(List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("ordinary")))),
                ModelContextCodec.safe(privateContent));
        ModelContextCodec codec = new ModelContextCodec();
        String encoded = codec.encode(privateContent);
        assertFalse(encoded.contains("private-signature"));
        assertFalse(encoded.contains("private"));
        assertEquals(ModelContextCodec.safe(privateContent), codec.decode(encoded));
        assertEquals(messages, codec.decode(codec.encode(safe)));
    }
}
