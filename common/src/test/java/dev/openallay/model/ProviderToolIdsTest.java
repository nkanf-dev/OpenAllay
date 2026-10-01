package dev.openallay.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProviderToolIdsTest {
    private static final String PROVIDER_ID = "call_" + "x".repeat(24);
    private static final String QUALIFIED_ID = "00000000-0000-0000-0000-000000000001:" + PROVIDER_ID;

    @Test
    void preservesValidIdsAtTheOpenAiSchemaBoundary() {
        String exactly64 = "a".repeat(64);
        String above64 = "a".repeat(65);
        List<ModelMessage> history = exchanges(PROVIDER_ID, exactly64, above64, "short:call", "调用");
        ProviderToolIds ids = ProviderToolIds.forOpenAiChat(history);

        assertEquals(PROVIDER_ID, ids.encode(PROVIDER_ID));
        assertEquals(exactly64, ids.encode(exactly64));
        for (String id : List.of(above64, "short:call", "调用")) {
            assertNotEquals(id, ids.encode(id));
            assertEquals(48, ids.encode(id).length());
            assertTrue(ids.encode(id).matches("[a-zA-Z0-9_-]+"));
        }
    }

    @Test
    void usesTheFullSha256DigestAndPreservesQualifiedRequestUniqueness() {
        String otherRequest = "00000000-0000-0000-0000-000000000002:" + PROVIDER_ID;
        List<ModelMessage> history = exchanges(QUALIFIED_ID, otherRequest, PROVIDER_ID);
        ProviderToolIds ids = ProviderToolIds.forOpenAiChat(history);

        assertEquals(66, QUALIFIED_ID.length());
        assertEquals("call_GT4TP8QG0Eh_V2hglfzLsgqo01l-QAeB9uh4r28b5oY", ids.encode(QUALIFIED_ID));
        assertNotEquals(ids.encode(QUALIFIED_ID), ids.encode(otherRequest));
        assertNotEquals(ids.encode(QUALIFIED_ID), ids.encode(PROVIDER_ID));
        assertEquals(PROVIDER_ID, ids.encode(PROVIDER_ID));
        // Both sides of each exchange retain the same original internal identity.
        assertEquals(QUALIFIED_ID, ((ModelContent.ToolUse) history.get(0).content().getFirst()).id());
        assertEquals(QUALIFIED_ID, ((ModelContent.ToolResult) history.get(1).content().getFirst()).toolUseId());
    }

    @Test
    void reservesValidOriginalIdsAndResolvesAliasCollisionsDeterministically() {
        String alias = ProviderToolIds.forOpenAiChat(exchanges(QUALIFIED_ID)).encode(QUALIFIED_ID);
        List<ModelMessage> history = exchanges(QUALIFIED_ID, alias, alias + "_1");
        ProviderToolIds ids = ProviderToolIds.forOpenAiChat(history);
        ArrayList<ModelMessage> reversed = new ArrayList<>(history);
        Collections.reverse(reversed);
        ProviderToolIds reordered = ProviderToolIds.forOpenAiChat(reversed);

        assertEquals(alias, ids.encode(alias));
        assertEquals(alias + "_1", ids.encode(alias + "_1"));
        assertEquals(alias + "_2", ids.encode(QUALIFIED_ID));
        assertEquals(ids.encode(QUALIFIED_ID), reordered.encode(QUALIFIED_ID));
        assertEquals(ids.encode(QUALIFIED_ID),
                ProviderToolIds.forOpenAiChat(history).encode(QUALIFIED_ID));
        assertTrue(ids.encode(QUALIFIED_ID).length() <= 64);
    }

    @Test
    void anthropicNormalizesItsAlphabetWithoutAnOpenAiLengthCap() {
        String longValid = "toolu_" + "a".repeat(100);
        ProviderToolIds ids = ProviderToolIds.forAnthropicMessages(exchanges(longValid, QUALIFIED_ID));

        assertEquals(longValid, ids.encode(longValid));
        assertEquals(ProviderToolIds.forOpenAiChat(exchanges(QUALIFIED_ID)).encode(QUALIFIED_ID),
                ids.encode(QUALIFIED_ID));
        assertTrue(ids.encode(QUALIFIED_ID).matches("[a-zA-Z0-9_-]+"));
    }

    @Test
    void refusesIdsOutsideTheRequestRatherThanSilentlyChangingCorrelation() {
        ProviderToolIds ids = ProviderToolIds.forOpenAiChat(exchanges(PROVIDER_ID));
        assertThrows(IllegalArgumentException.class, () -> ids.encode("not_in_this_request"));
    }

    private static List<ModelMessage> exchanges(String... ids) {
        ArrayList<ModelMessage> messages = new ArrayList<>();
        for (String id : ids) {
            messages.add(new ModelMessage(ModelRole.ASSISTANT,
                    List.of(new ModelContent.ToolUse(id, "fact", new JsonObject()))));
            messages.add(new ModelMessage(ModelRole.USER,
                    List.of(new ModelContent.ToolResult(id, new JsonPrimitive("fact"), false))));
        }
        return List.copyOf(messages);
    }
}
