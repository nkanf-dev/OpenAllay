package dev.openallay.agent.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ModelContextCodecTest {
    private final ModelContextCodec codec = new ModelContextCodec();

    @Test
    void roundTripsActualOrderedMessagesCallsResultsAndErrorFlagsWithoutSyntheticProjection() {
        List<ModelMessage> messages = transcript();

        String encoded = codec.encode(messages);
        List<ModelMessage> decoded = codec.decode(encoded);

        assertEquals(messages, decoded);
        assertEquals(encoded, codec.encode(decoded));
        assertFalse(encoded.contains("durableProjection"));
        assertEquals(2, ContextStructure.units(decoded).stream()
                .filter(ContextStructure.Unit::toolExchange).count());
        ModelContent.ToolUse source = assertInstanceOf(ModelContent.ToolUse.class,
                decoded.get(1).content().get(1));
        assertEquals("return mc.game.diagnostics.filter(function (row) { return row.name === 'biome'; });",
                source.input().get("source").getAsString());
        ModelContent.ToolResult failure = assertInstanceOf(ModelContent.ToolResult.class,
                decoded.get(2).content().getFirst());
        assertTrue(failure.error());
        assertEquals("status: failure\ncode: javascript_error\nmessage: .filter is undefined",
                failure.value().getAsString());
        ModelContent.ToolResult success = assertInstanceOf(ModelContent.ToolResult.class,
                decoded.get(4).content().getFirst());
        assertFalse(success.error());
        assertEquals(JsonParser.parseString("{\"rows\":[{\"name\":\"biome\",\"value\":\"平原\"}],\"count\":1}"),
                success.value());
        JsonObject envelope = JsonParser.parseString(encoded).getAsJsonObject();
        assertEquals(Set.of("messages"), envelope.keySet());
        assertEquals(Set.of("role", "content"),
                envelope.getAsJsonArray("messages").get(1).getAsJsonObject().keySet());
    }

    @Test
    void excludesPrivateReasoningTextAndSignaturesEvenFromReasoningOnlyMessages() {
        List<ModelMessage> actual = transcript();
        List<ModelMessage> withReasoning = new ArrayList<>();
        withReasoning.add(actual.getFirst());
        withReasoning.add(new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.Reasoning("private-only-thought", "private-only-signature"))));
        List<ModelContent> assistant = new ArrayList<>(actual.get(1).content());
        assistant.addFirst(new ModelContent.Reasoning("private-planning-thought", "private-planning-signature"));
        withReasoning.add(new ModelMessage(ModelRole.ASSISTANT, assistant));
        withReasoning.addAll(actual.subList(2, actual.size()));

        String encoded = codec.encode(withReasoning);
        List<ModelMessage> decoded = codec.decode(encoded);

        assertEquals(actual, decoded);
        assertEquals(actual, ModelContextCodec.safe(withReasoning));
        assertFalse(encoded.contains("private-only"));
        assertFalse(encoded.contains("private-planning"));
        assertFalse(encoded.contains("reasoning"));
        assertFalse(encoded.contains("signature"));
        assertTrue(decoded.stream().flatMap(message -> message.content().stream())
                .noneMatch(ModelContent.Reasoning.class::isInstance));
        assertEquals(List.of(), codec.decode(codec.encode(List.of(new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.Reasoning("private", "signature")))))));
    }

    @Test
    void rejectsMalformedEnvelopesMissingMessagesAndAnyUnknownEnvelopeField() {
        assertThrows(RuntimeException.class, () -> codec.decode("{"), "malformed JSON syntax");
        for (String json : List.of(
                "null", "[]", "true", "\"transcript\"", "{}",
                "{\"messages\":null}", "{\"messages\":{}}", "{\"messages\":\"[]\"}",
                "{\"messages\":[],\"summary\":\"display history\"}",
                "{\"messages\":[],\"unknown\":true}")) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(json), json);
        }
        JsonObject envelope = JsonParser.parseString("{\"messages\":[]}").getAsJsonObject();
        envelope.addProperty("version", 1);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(envelope.toString()),
                "a version field is an unknown field, not a compatibility path");
        assertEquals(List.of(), codec.decode("{\"messages\":[]}"));
    }

    @Test
    void rejectsUnknownMissingAndWrongTypedMessageAndContentFields() {
        for (String message : List.of(
                "null", "[]", "\"not a message\"", "{}",
                "{\"role\":\"USER\"}", "{\"content\":[]}",
                "{\"role\":\"SYSTEM\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}",
                "{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}",
                "{\"role\":null,\"content\":[]}", "{\"role\":false,\"content\":[]}",
                "{\"role\":\"USER\",\"content\":null}", "{\"role\":\"USER\",\"content\":{}}",
                "{\"role\":\"USER\",\"content\":[]}",
                "{\"role\":\"USER\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}],\"display\":true}")) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(envelope(message)), message);
        }
        for (String item : List.of(
                "null", "[]", "1", "{}",
                "{\"type\":null}", "{\"type\":true}",
                "{\"type\":\"thinking\",\"thinking\":\"private\",\"signature\":\"private\"}",
                "{\"type\":\"reasoning\",\"text\":\"private\"}",
                "{\"type\":\"text\"}", "{\"type\":\"text\",\"text\":null}",
                "{\"type\":\"text\",\"text\":9}", "{\"type\":\"text\",\"text\":true}",
                "{\"type\":\"text\",\"text\":\"hi\",\"modelText\":\"summary\"}")) {
            String message = "{\"role\":\"USER\",\"content\":[" + item + "]}";
            assertThrows(IllegalArgumentException.class, () -> codec.decode(envelope(message)), item);
        }
    }

    @Test
    void rejectsWrongTypedMissingAndExtraToolFieldsWithoutCoercingErrorFlag() {
        JsonObject valid = JsonParser.parseString(codec.encode(transcript())).getAsJsonObject();
        for (String field : List.of("type", "id", "name", "input")) {
            JsonObject broken = valid.deepCopy();
            content(broken, 1, 1).remove(field);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(broken.toString()), field);
        }
        for (String field : List.of("type", "toolUseId", "value", "error")) {
            JsonObject broken = valid.deepCopy();
            content(broken, 2, 0).remove(field);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(broken.toString()), field);
        }
        for (String value : List.of("null", "0", "\"false\"", "{}", "[]")) {
            JsonObject broken = valid.deepCopy();
            content(broken, 2, 0).add("error", JsonParser.parseString(value));
            assertThrows(IllegalArgumentException.class, () -> codec.decode(broken.toString()), value);
        }
        for (String value : List.of("null", "1", "\"{}\"", "[]")) {
            JsonObject broken = valid.deepCopy();
            content(broken, 1, 1).add("input", JsonParser.parseString(value));
            assertThrows(IllegalArgumentException.class, () -> codec.decode(broken.toString()), value);
        }
        for (String field : List.of("id", "name")) {
            for (String value : List.of("null", "true", "9", "\"\"", "\"  \"")) {
                JsonObject broken = valid.deepCopy();
                content(broken, 1, 1).add(field, JsonParser.parseString(value));
                assertThrows(IllegalArgumentException.class, () -> codec.decode(broken.toString()), field + ": " + value);
            }
        }
        for (int[] position : List.of(new int[] {1, 1}, new int[] {2, 0})) {
            JsonObject broken = valid.deepCopy();
            content(broken, position[0], position[1]).addProperty("displaySummary", "must not be a model message");
            assertThrows(IllegalArgumentException.class, () -> codec.decode(broken.toString()));
        }
    }

    @Test
    void rejectsOrphanMissingDuplicateReorderedAndWrongRoleToolPairs() {
        JsonObject valid = JsonParser.parseString(codec.encode(transcript())).getAsJsonObject();
        JsonObject missingResult = valid.deepCopy();
        missingResult.getAsJsonArray("messages").remove(2);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(missingResult.toString()));
        JsonObject orphanResult = valid.deepCopy();
        orphanResult.getAsJsonArray("messages").remove(1);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(orphanResult.toString()));
        JsonObject duplicateId = valid.deepCopy();
        content(duplicateId, 3, 0).addProperty("id", "failed_filter");
        content(duplicateId, 4, 0).addProperty("toolUseId", "failed_filter");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(duplicateId.toString()));
        JsonObject mismatchedId = valid.deepCopy();
        content(mismatchedId, 2, 0).addProperty("toolUseId", "unknown_call");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(mismatchedId.toString()));
        JsonObject wrongUseRole = valid.deepCopy();
        wrongUseRole.getAsJsonArray("messages").get(1).getAsJsonObject().addProperty("role", "USER");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(wrongUseRole.toString()));
        JsonObject wrongResultRole = valid.deepCopy();
        wrongResultRole.getAsJsonArray("messages").get(2).getAsJsonObject().addProperty("role", "ASSISTANT");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(wrongResultRole.toString()));
        JsonObject mixedResult = valid.deepCopy();
        mixedResult.getAsJsonArray("messages").get(2).getAsJsonObject().getAsJsonArray("content")
                .add(JsonParser.parseString("{\"type\":\"text\",\"text\":\"display note\"}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(mixedResult.toString()));

        JsonObject parallel = valid.deepCopy();
        JsonArray uses = parallel.getAsJsonArray("messages").get(1).getAsJsonObject().getAsJsonArray("content");
        JsonObject secondUse = uses.get(1).getAsJsonObject().deepCopy();
        secondUse.addProperty("id", "second_parallel");
        uses.add(secondUse);
        JsonArray results = parallel.getAsJsonArray("messages").get(2).getAsJsonObject().getAsJsonArray("content");
        JsonObject secondResult = results.get(0).getAsJsonObject().deepCopy();
        secondResult.addProperty("toolUseId", "second_parallel");
        results.add(secondResult);
        assertEquals(2, codec.decode(parallel.toString()).get(2).content().size());
        JsonElement firstResult = results.remove(0);
        results.add(firstResult);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(parallel.toString()));
    }

    @Test
    void preservesPlayerTextAndCredentialNamedFieldsInNestedToolSourceAndResults() {
        String known = "opaque-known-value";
        String longerKnown = known + "/suffix";
        String source = "var key = \"" + known + "\";\n"
                + "var authorization = \"Bearer " + "HeaderPrivate987654321\";\nreturn {count: 2};";
        String modelText = "api-key=InlinePrivateValue\ncookie: SessionPrivateValue\n"
                + "Bearer " + "BearerPrivate987654321\nsk-privateabc123456789\npk-privateabc123456789\n"
                + "observed " + longerKnown;
        JsonObject nested = new JsonObject();
        nested.addProperty("source", source);
        nested.addProperty("modelText", modelText);
        nested.addProperty("Authorization", "Bearer " + "HeaderPrivate987654321");
        nested.addProperty("x-api-key", "HeaderPrivate987654321");
        nested.addProperty("api_key", "KeyPrivate987654321");
        nested.addProperty("access-token", "AccessPrivate987654321");
        nested.addProperty("token", "TokenPrivate987654321");
        nested.addProperty("password", "PasswordPrivate987654321");
        nested.addProperty("secret", "SecretPrivate987654321");
        nested.addProperty("Cookie", "session=CookiePrivate987654321");
        nested.addProperty("Set-Cookie", "session=SetCookiePrivate987654321");
        nested.addProperty("reasoning", "player-provided reasoning is ordinary tool data");
        nested.addProperty("signature", "player-provided signature is ordinary tool data");
        nested.addProperty("recipeId", "test:token_recipe");
        nested.addProperty("count", 2);
        nested.addProperty("complete", false);
        JsonArray rows = new JsonArray();
        rows.add(nested);
        rows.add(new JsonPrimitive("preserve-unrelated-text"));
        JsonObject input = new JsonObject();
        input.add("nested", rows.deepCopy());
        JsonObject output = new JsonObject();
        output.add("nested", rows.deepCopy());
        String question = "Keep " + known + " private. token=QuestionPrivateValue";
        List<ModelMessage> messages = List.of(
                ModelMessage.userText(question),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.ToolUse("original_call", "openallay__run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(
                        new ModelContent.ToolResult("original_call", output, true))));
        String originalEncoded = codec.encode(messages);

        List<ModelMessage> safe = ModelContextCodec.safe(messages);

        assertEquals(messages, safe);
        assertEquals(question,
                assertInstanceOf(ModelContent.Text.class, safe.getFirst().content().getFirst()).text());
        ModelContent.ToolUse use = assertInstanceOf(ModelContent.ToolUse.class,
                safe.get(1).content().getFirst());
        ModelContent.ToolResult result = assertInstanceOf(ModelContent.ToolResult.class,
                safe.get(2).content().getFirst());
        assertEquals("original_call", use.id());
        assertEquals("openallay__run_javascript", use.name());
        assertEquals("original_call", result.toolUseId());
        assertTrue(result.error());
        JsonObject safeInput = use.input().getAsJsonArray("nested").get(0).getAsJsonObject();
        JsonObject safeOutput = result.value().getAsJsonObject()
                .getAsJsonArray("nested").get(0).getAsJsonObject();
        assertEquals(nested, safeInput);
        assertEquals(nested, safeOutput);
        assertEquals(source, safeInput.get("source").getAsString());
        assertEquals(modelText, safeInput.get("modelText").getAsString());
        for (String key : List.of("Authorization", "x-api-key", "api_key", "access-token", "token",
                "password", "secret", "Cookie", "Set-Cookie", "reasoning", "signature")) {
            assertEquals(nested.get(key), safeInput.get(key), key);
        }
        assertEquals("test:token_recipe", safeInput.get("recipeId").getAsString());
        assertEquals(new JsonPrimitive(2), safeInput.get("count"));
        assertEquals(new JsonPrimitive(false), safeInput.get("complete"));
        assertEquals("preserve-unrelated-text", use.input().getAsJsonArray("nested").get(1).getAsString());
        String encoded = codec.encode(safe);
        for (String value : List.of(known, longerKnown, "HeaderPrivate987654321", "InlinePrivateValue",
                "SessionPrivateValue", "BearerPrivate987654321", "sk-privateabc123456789",
                "pk-privateabc123456789", "QuestionPrivateValue")) {
            assertTrue(encoded.contains(value), value);
        }
        assertEquals(safe, codec.decode(encoded));
        assertEquals(safe, ModelContextCodec.safe(safe));
        assertEquals(originalEncoded, codec.encode(messages),
                "the context boundary must not mutate live model messages");
        assertEquals(source, input.getAsJsonArray("nested").get(0).getAsJsonObject().get("source").getAsString());
        assertEquals(source, assertInstanceOf(ModelContent.ToolUse.class,
                messages.get(1).content().getFirst()).input()
                .getAsJsonArray("nested").get(0).getAsJsonObject().get("source").getAsString());
        assertEquals(modelText, assertInstanceOf(ModelContent.ToolResult.class,
                messages.get(2).content().getFirst()).value().getAsJsonObject()
                .getAsJsonArray("nested").get(0).getAsJsonObject().get("modelText").getAsString());
    }

    @Test
    void preservesCredentialLikePlayerTextWithoutContentClassification() {
        String playerText = "Authorization: Bearer " + "HeaderPrivate987654321\nx-api-key: HeaderPrivateValue\n"
                + "password=PasswordPrivateValue\naccess_token='AccessPrivateValue'\n"
                + "cookie=SessionPrivateValue\nBearer " + "BearerPrivate987654321\n"
                + "sk-privateabc123456789\nordinary-source-id";
        List<ModelMessage> actual = List.of(ModelMessage.userText(playerText));

        List<ModelMessage> safe = ModelContextCodec.safe(actual);

        assertEquals(actual, safe);
        assertEquals(actual, codec.decode(codec.encode(actual)),
                "encoding must preserve the actual player message");
        assertEquals(playerText,
                assertInstanceOf(ModelContent.Text.class, safe.getFirst().content().getFirst()).text());
        assertEquals(safe, ModelContextCodec.safe(safe));
        assertEquals(transcript(), ModelContextCodec.safe(transcript()),
                "the exact .filter failure, source code, ordinary evidence, and flags must survive");
    }

    @Test
    void preservesBasicAndCookieHeadersRefreshTokensAndCompleteUrlsIdempotently() {
        String basic = "dXNlcjpwYXNz";
        String privateUrl = "https://user:pass@example.invalid/guide/path?view=guide#entry";
        String ordinaryUrl = "https://example.invalid/guide/path?view=guide#entry";
        String headerText = "Authorization: Basic " + basic + "\n"
                + "Cookie:a=private; b=hidden\nSet-Cookie: c=private; HttpOnly; Secure\n"
                + "refresh_token=RefreshPrivateValue\n"
                + "Endpoint: " + privateUrl + "\nPublic endpoint: " + ordinaryUrl;
        String source = "var cookie = \"a=private; b=hidden\";\n"
                + "var refresh_token = 'RefreshPrivateValue';\n"
                + "var endpoint = \"" + privateUrl + "\";\n"
                + "return {endpoint: endpoint, count: 2};";
        JsonObject nested = new JsonObject();
        nested.addProperty("source", source);
        nested.addProperty("modelText", headerText);
        nested.addProperty("refresh_token", "NestedRefreshPrivateValue");
        nested.addProperty("Authorization", "Basic " + basic);
        nested.addProperty("Cookie", "a=private; b=hidden");
        nested.addProperty("Set-Cookie", "c=private; HttpOnly; Secure");
        nested.addProperty("endpoint", privateUrl);
        nested.addProperty("publicEndpoint", ordinaryUrl);
        nested.addProperty("count", 2);
        JsonObject input = new JsonObject();
        input.add("nested", nested.deepCopy());
        JsonObject output = new JsonObject();
        output.add("nested", nested.deepCopy());
        List<ModelMessage> actual = List.of(
                ModelMessage.userText(headerText),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        "credential_shapes", "openallay__run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "credential_shapes", output, false))));

        List<ModelMessage> safe = ModelContextCodec.safe(actual);

        assertEquals(actual, safe);
        assertEquals(headerText,
                assertInstanceOf(ModelContent.Text.class, safe.getFirst().content().getFirst()).text());
        ModelContent.ToolUse use = assertInstanceOf(ModelContent.ToolUse.class,
                safe.get(1).content().getFirst());
        ModelContent.ToolResult result = assertInstanceOf(ModelContent.ToolResult.class,
                safe.get(2).content().getFirst());
        assertEquals("credential_shapes", use.id());
        assertEquals(use.id(), result.toolUseId());
        assertFalse(result.error());
        JsonObject safeInput = use.input().getAsJsonObject("nested");
        assertEquals(nested, safeInput);
        assertEquals(nested, result.value().getAsJsonObject().getAsJsonObject("nested"));
        assertEquals(source, safeInput.get("source").getAsString());
        assertEquals(headerText, safeInput.get("modelText").getAsString());
        assertEquals("NestedRefreshPrivateValue", safeInput.get("refresh_token").getAsString());
        assertEquals("Basic " + basic, safeInput.get("Authorization").getAsString());
        assertEquals("a=private; b=hidden", safeInput.get("Cookie").getAsString());
        assertEquals("c=private; HttpOnly; Secure", safeInput.get("Set-Cookie").getAsString());
        assertEquals(privateUrl, safeInput.get("endpoint").getAsString());
        assertEquals(ordinaryUrl, safeInput.get("publicEndpoint").getAsString());
        assertEquals(new JsonPrimitive(2), safeInput.get("count"));
        assertEquals(safe, ModelContextCodec.safe(safe),
                "the context boundary cannot alter quote, semicolon, URL, or following statement syntax");
        assertEquals(actual, codec.decode(codec.encode(actual)));
        String encoded = codec.encode(safe);
        for (String value : List.of(basic, "a=private", "b=hidden", "c=private", "RefreshPrivateValue",
                "NestedRefreshPrivateValue", "user:pass", privateUrl, ordinaryUrl)) {
            assertTrue(encoded.contains(value), value);
        }
        assertEquals(source, assertInstanceOf(ModelContent.ToolUse.class,
                actual.get(1).content().getFirst()).input().getAsJsonObject("nested")
                .get("source").getAsString());
    }

    @Test
    void preservesCredentialLikeHeadersAfterNativeExceptionPrefixInNestedResults() {
        String errorText = "IllegalArgumentException: Cookie: first=private; second=hidden\n"
                + "at guide.js:8: filter is undefined";
        String authorizationError = "TypeError: Authorization: Digest secret=private, nonce=hidden";
        JsonObject nested = new JsonObject();
        nested.addProperty("errorText", errorText);
        nested.addProperty("authorizationError", authorizationError);
        JsonObject result = new JsonObject();
        result.add("nested", nested);
        JsonObject input = new JsonObject();
        input.addProperty("source", "return mc.items.values.filter(function (row) { return row.id; });");
        List<ModelMessage> actual = List.of(
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        "prefixed_error", "openallay__run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "prefixed_error", result, true))));

        List<ModelMessage> safe = ModelContextCodec.safe(actual);

        assertEquals(actual, safe);
        ModelContent.ToolResult error = assertInstanceOf(ModelContent.ToolResult.class,
                safe.getLast().content().getFirst());
        assertTrue(error.error());
        assertEquals("prefixed_error", error.toolUseId());
        JsonObject details = error.value().getAsJsonObject().getAsJsonObject("nested");
        assertEquals(nested, details);
        assertEquals(errorText, details.get("errorText").getAsString());
        assertEquals(authorizationError, details.get("authorizationError").getAsString());
        assertEquals(safe, ModelContextCodec.safe(safe));
        assertEquals(actual, codec.decode(codec.encode(actual)));
        assertEquals(input, assertInstanceOf(ModelContent.ToolUse.class,
                safe.getFirst().content().getFirst()).input());
        assertEquals(result, assertInstanceOf(ModelContent.ToolResult.class,
                actual.getLast().content().getFirst()).value());
    }

    private static String envelope(String message) {
        return "{\"messages\":[" + message + "]}";
    }

    private static JsonObject content(JsonObject envelope, int message, int item) {
        return envelope.getAsJsonArray("messages").get(message).getAsJsonObject()
                .getAsJsonArray("content").get(item).getAsJsonObject();
    }

    private static List<ModelMessage> transcript() {
        JsonObject failedInput = new JsonObject();
        failedInput.addProperty("source",
                "return mc.game.diagnostics.filter(function (row) { return row.name === 'biome'; });");
        JsonObject correctedInput = new JsonObject();
        correctedInput.addProperty("source",
                "return mc.game.diagnostics.values.filter(function (row) { return row.name === 'biome'; });");
        return List.of(
                ModelMessage.userText("Find my biome. 请保留原始错误。"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.Text("I will inspect diagnostics."),
                        new ModelContent.ToolUse("failed_filter", "openallay__run_javascript", failedInput))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("failed_filter",
                        new JsonPrimitive("status: failure\ncode: javascript_error\nmessage: .filter is undefined"), true))),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.ToolUse("corrected_filter", "openallay__run_javascript", correctedInput))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("corrected_filter",
                        JsonParser.parseString("{\"rows\":[{\"name\":\"biome\",\"value\":\"平原\"}],\"count\":1}"), false))),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("Observed biome: 平原."))));
    }
}
