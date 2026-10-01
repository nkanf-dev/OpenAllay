package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.model.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class KnownSecretRedactorTest {
    @Test
    void scrubsValuesAndPropertyNamesWithoutLosingCollidingFieldsOrMutatingFacts() {
        String first = "opaqueSavedCredentialAlpha";
        String second = "opaqueSavedCredentialBeta";
        KnownSecretRedactor redactor = new KnownSecretRedactor(Set.of(first, second));
        JsonObject raw = new JsonObject();
        raw.addProperty(first, "ordinary-a");
        raw.addProperty(second, "ordinary-b");
        raw.addProperty("[REDACTED]", "ordinary-c");
        raw.addProperty("fact", 42);
        raw.addProperty("observed", first);
        JsonObject safe = redactor.json(raw).getAsJsonObject();
        assertFalse(safe.toString().contains(first));
        assertFalse(safe.toString().contains(second));
        assertEquals(raw.size(), safe.size());
        assertEquals(42, safe.get("fact").getAsInt());
        assertEquals("ordinary-c", safe.get("[REDACTED]").getAsString());
        assertEquals(safe, redactor.json(safe));
        assertTrue(raw.has(first));
    }

    @Test
    void secretBearingInvocationIdsKeepDistinctPairing() {
        String secret = "opaqueSavedCredential";
        KnownSecretRedactor redactor = new KnownSecretRedactor(Set.of(secret));
        JsonObject input = new JsonObject();
        List<ModelMessage> raw = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.ToolUse(secret + "-a", "test__fact", input),
                new ModelContent.ToolUse(secret + "-b", "test__fact", input))),
                new ModelMessage(ModelRole.USER, List.of(
                        new ModelContent.ToolResult(secret + "-a", input, false),
                        new ModelContent.ToolResult(secret + "-b", input, false))));
        List<ModelMessage> safe = redactor.messages(raw);
        assertFalse(new Gson().toJson(safe).contains(secret));
        assertNotEquals(((ModelContent.ToolUse) safe.getFirst().content().getFirst()).id(),
                ((ModelContent.ToolUse) safe.getFirst().content().getLast()).id());
        assertEquals(safe, new ModelContextCodec().decode(new ModelContextCodec().encode(safe)));
    }

    @Test
    void retainsUnchangedImmutableContentIdentityAndForgetsPrivateReasoning() {
        KnownSecretRedactor redactor = new KnownSecretRedactor(Set.of("opaqueSavedCredential"));
        JsonObject input = new JsonObject();
        input.addProperty("source", "return 42;");
        ModelContent.ToolUse use = new ModelContent.ToolUse("call", "test__fact", input);
        ModelContent.ToolResult result = new ModelContent.ToolResult("call", input, false);
        List<ModelMessage> messages = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(use)),
                new ModelMessage(ModelRole.USER, List.of(result)));
        List<ModelMessage> safe = redactor.messages(messages);
        assertSame(messages.getFirst(), safe.getFirst());
        assertSame(use, safe.getFirst().content().getFirst());
        assertSame(result, safe.getLast().content().getFirst());
        assertEquals(List.of(new ModelContent.Text("ordinary")), redactor.content(List.of(
                new ModelContent.Reasoning("private", "private-signature"), new ModelContent.Text("ordinary"))));
        assertEquals(messages, new ModelContextCodec().decode(new ModelContextCodec().encode(safe)));
    }
}
