package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuideToolPresentationTest {
    @Test
    void onlyCurrentToolsHaveSpecialInvocationMessages() {
        JsonObject skill = new JsonObject();
        skill.addProperty("name", "analyze-game-data");
        assertEquals(
                List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.INVOCATION_LOAD_SKILL_EXACT,
                        "analyze-game-data")),
                GuideToolInvocationPresentation.messages("openallay:load_skill", skill));
        assertEquals(
                List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT)),
                GuideToolInvocationPresentation.messages(
                        "openallay:run_javascript", new JsonObject()));
        assertTrue(GuideToolInvocationPresentation
                .messages("openallay:removed_domain_tool", new JsonObject())
                .isEmpty());
    }

    @Test
    void distinguishesSkillInstructionLoadFromExactReferenceReads() {
        JsonObject instructions = JsonParser.parseString(
                "{\"name\":\"analyze-game-data\"}").getAsJsonObject();
        assertEquals(
                List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.INVOCATION_LOAD_SKILL_EXACT,
                        "analyze-game-data")),
                GuideToolInvocationPresentation.messages("openallay:load_skill", instructions));

        JsonObject reference = JsonParser.parseString("""
                {"name":"analyze-game-data","reference":"references/recipes.md"}
                """).getAsJsonObject();
        assertEquals(
                List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.INVOCATION_LOAD_SKILL_REFERENCE,
                        "analyze-game-data",
                        "references/recipes.md")),
                GuideToolInvocationPresentation.messages("openallay:load_skill", reference));

        assertTrue(GuideToolInvocationPresentation.messages(
                        "openallay:run_javascript", reference)
                .stream()
                .noneMatch(message -> message.key()
                        == GuideToolMessage.Key.INVOCATION_LOAD_SKILL_REFERENCE));
        assertTrue(GuideToolInvocationPresentation.messages(
                        "openallay:future_tool", reference).isEmpty());
    }

    @Test
    void projectsJavascriptAndSkillResultsWithoutLegacyDomainNarration() {
        JsonObject javascript = JsonParser.parseString("""
                {"status":"success","value":{"resultType":"array","cardinality":9,"complete":false,
                  "preview":[{},{}]}}
                """).getAsJsonObject();
        assertEquals(
                List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.ANALYSIS_PREVIEW, "2", "9")),
                GuideToolPresentation.messages("openallay:run_javascript", javascript));

        JsonObject skill = JsonParser.parseString("""
                {"status":"success","value":{"name":"analyze-game-data",
                  "allowedTools":["openallay:run_javascript"],"provenance":"bundled"}}
                """).getAsJsonObject();
        assertEquals(
                List.of(
                        GuideToolMessage.of(
                                GuideToolMessage.Key.SKILL_LOADED, "analyze-game-data"),
                        GuideToolMessage.of(
                                GuideToolMessage.Key.SKILL_TOOLS, "1", "bundled")),
                GuideToolPresentation.messages("openallay:load_skill", skill));

        JsonObject generic = JsonParser.parseString(
                "{\"status\":\"success\",\"value\":{\"internal\":\"not projected\"}}")
                .getAsJsonObject();
        assertEquals(
                List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED)),
                GuideToolPresentation.messages("openallay:future_tool", generic));
    }

    @Test
    void completedArrayCalculationsCountPreviewItemsWithoutInventingProgress() {
        for (String preview : List.of("[1]", "[null,true]", "[[1,2]]", "[{\"id\":\"a\"}]")) {
            JsonObject normalized = JsonParser.parseString("""
                    {"status":"success","value":{"resultType":"array","cardinality":5,
                     "complete":false,"preview":%s}}
                    """.formatted(preview)).getAsJsonObject();
            String shown = Integer.toString(JsonParser.parseString(preview).getAsJsonArray().size());
            assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_PREVIEW,
                    shown, "5")), GuideToolPresentation.messages("openallay:run_javascript", normalized));
        }
    }

    @Test
    void objectCountsMeanTopLevelFieldsEvenWhenOnlyNestedValuesAreSampled() {
        JsonObject normalized = JsonParser.parseString("""
                {"status":"success","value":{"resultType":"object","cardinality":5,
                 "complete":false,"preview":{"rows":[{"id":"a"}],"count":9,
                   "ids":["a","b"],"nested":{"kept":true},"ready":true}}}
                """).getAsJsonObject();
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_FIELDS_PREVIEW,
                "5", "5")), GuideToolPresentation.messages("openallay:run_javascript", normalized));
        normalized.getAsJsonObject("value").addProperty("complete", true);
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_FIELDS_COMPLETE,
                "5")), GuideToolPresentation.messages("openallay:run_javascript", normalized));
        normalized.getAsJsonObject("value").addProperty("complete", false);
        normalized.getAsJsonObject("value").add("preview", JsonParser.parseString("{\"count\":9}"));
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_FIELDS_PREVIEW,
                "1", "5")), GuideToolPresentation.messages("openallay:run_javascript", normalized));
    }

    @Test
    void completePrimitiveArrayReportsCalculationSizeNotFullRenderedText() {
        JsonObject normalized = JsonParser.parseString("""
                {"status":"success","value":{"resultType":"array","cardinality":5,
                 "complete":true,"preview":[null,true,"id",42,"%s"]}}
                """.formatted("x".repeat(400))).getAsJsonObject();
        String original = normalized.toString();
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_COMPLETE, "5")),
                GuideToolPresentation.messages("openallay:run_javascript", normalized));
        assertEquals(original, normalized.toString());
    }

    @Test
    void unavailableContainerPreviewDoesNotCountTheOmissionMarkerAsOneResult() {
        for (String type : List.of("array", "object")) {
            JsonObject normalized = JsonParser.parseString("""
                    {"status":"success","value":{"resultType":"%s","cardinality":5,
                     "complete":false,"preview":"…"}}
                    """.formatted(type)).getAsJsonObject();
            GuideToolMessage.Key key = type.equals("array")
                    ? GuideToolMessage.Key.ANALYSIS_PREVIEW
                    : GuideToolMessage.Key.ANALYSIS_FIELDS_PREVIEW;
            assertEquals(List.of(GuideToolMessage.of(key, "0", "5")),
                    GuideToolPresentation.messages("openallay:run_javascript", normalized));
        }
    }

    @Test
    void scalarAndEmptyResultsDoNotInventMatchingRowsOrFullDisplayClaims() {
        for (String encoded : List.of("null", "42", "true", "\"answer\"")) {
            String type = encoded.equals("null") ? "null" : encoded.equals("true") ? "boolean"
                    : encoded.equals("42") ? "number" : "string";
            JsonObject normalized = JsonParser.parseString("""
                    {"status":"success","value":{"resultType":"%s","complete":true,"preview":%s}}
                    """.formatted(type, encoded)).getAsJsonObject();
            assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_VALUE_COMPLETE)),
                    GuideToolPresentation.messages("openallay:run_javascript", normalized));
        }
        for (String encoded : List.of("[]", "{}")) {
            String type = encoded.equals("[]") ? "array" : "object";
            JsonObject normalized = JsonParser.parseString("""
                    {"status":"success","value":{"resultType":"%s","cardinality":0,
                     "complete":true,"preview":%s}}
                    """.formatted(type, encoded)).getAsJsonObject();
            GuideToolMessage.Key key = encoded.equals("[]")
                    ? GuideToolMessage.Key.ANALYSIS_EMPTY : GuideToolMessage.Key.ANALYSIS_FIELDS_COMPLETE;
            assertEquals(encoded.equals("[]") ? List.of(GuideToolMessage.of(key))
                    : List.of(GuideToolMessage.of(key, "0")),
                    GuideToolPresentation.messages("openallay:run_javascript", normalized));
        }
        JsonObject partial = JsonParser.parseString("""
                {"status":"success","value":{"resultType":"string","cardinality":1,
                 "complete":false,"preview":"partial…"}}
                """).getAsJsonObject();
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_VALUE_PREVIEW)),
                GuideToolPresentation.messages("openallay:run_javascript", partial));
    }

    @Test
    void workspaceReceiptRequiresARealHandleAndDoesNotChangeExecutionStatus() {
        JsonObject normalized = JsonParser.parseString("""
                {"status":"success","value":{"handle":"r_current","resultType":"array",
                 "cardinality":5,"complete":false,"preview":[1]}}
                """).getAsJsonObject();
        assertEquals(List.of(
                        GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_PREVIEW, "1", "5"),
                        GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_WORKSPACE)),
                GuideToolPresentation.messages("openallay:run_javascript", normalized));
        normalized.addProperty("status", "failure");
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.FAILURE_GENERIC)),
                GuideToolPresentation.messages("openallay:run_javascript", normalized));
    }

    @Test
    void restoredToolWithoutStoredResultIsNotPresentedAsStillRunning() {
        assertEquals(
                List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_DETAIL_NOT_STORED)),
                GuideToolPresentation.messages("openallay:run_javascript", null));
    }

    @Test
    void detailProjectionSeparatesRunningFromRestoredMissingResults() {
        for (GuideToolStatus status : GuideToolStatus.values()) {
            GuideToolActivity activity = new GuideToolActivity(
                    "call-1", 0, "openallay:run_javascript", status, null, List.of(), List.of());
            var detail = dev.openallay.guide.ui.GuideToolDetailPresenter.project(activity, false);
            assertEquals(status, detail.status());
            assertEquals(List.of(GuideToolMessage.of(status == GuideToolStatus.RUNNING
                    ? GuideToolMessage.Key.RESULT_PENDING
                    : GuideToolMessage.Key.RESULT_DETAIL_NOT_STORED)), detail.narration());
        }
    }

    @Test
    void failuresUseClosedFriendlyMessages() {
        JsonObject normalized = JsonParser.parseString(
                "{\"status\":\"failure\",\"code\":\"stale_reference\",\"message\":\"reload\"}")
                .getAsJsonObject();
        assertEquals(
                List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.FAILURE_STALE_REFERENCE)),
                GuideToolPresentation.messages("openallay:run_javascript", normalized));
    }

    @Test
    void invalidInputCodeVariantsUseTheSameNeutralActionMessageWithoutRawReasons() {
        for (String code : List.of("invalid_arguments", "invalid_tool_arguments")) {
            JsonObject normalized = new JsonObject();
            normalized.addProperty("status", "failure");
            normalized.addProperty("code", code);
            normalized.addProperty("message", "secret-value private-endpoint java.lang.RuntimeException");
            List<GuideToolMessage> messages = GuideToolPresentation.messages("openallay:run_javascript", normalized);
            assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.FAILURE_INVALID_ARGUMENTS)), messages);
            assertTrue(messages.stream().allMatch(message -> message.arguments().isEmpty()));
        }
    }

    @Test
    void strictCodecRoundTripsClosedMessagesAndRejectsSchemaDrift() {
        List<GuideToolMessage> expected = List.of(
                GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED),
                GuideToolMessage.of(
                        GuideToolMessage.Key.ANALYSIS_PREVIEW,
                        "2",
                        "9"));
        assertEquals(expected, GuideToolMessageCodec.decode(GuideToolMessageCodec.encode(expected)));

        assertThrows(IllegalArgumentException.class, () -> GuideToolMessageCodec.decode(
                JsonParser.parseString("[{\"key\":\"RESULT_COMPLETED\",\"arguments\":[],\"extra\":true}]")));
        assertThrows(IllegalArgumentException.class, () -> GuideToolMessageCodec.decode(
                JsonParser.parseString("[{\"key\":\"FUTURE_KEY\",\"arguments\":[]}]")));
        assertThrows(IllegalArgumentException.class, () -> GuideToolMessageCodec.decode(
                JsonParser.parseString("[{\"key\":\"RESULT_COMPLETED\",\"arguments\":[1]}]")));
        assertThrows(IllegalArgumentException.class, () -> GuideToolMessageCodec.decode(
                JsonParser.parseString("[{\"key\":\"RESULT_COMPLETED\",\"arguments\":[\"bad\\nvalue\"]}]")));
    }

    @Test
    void resultLabelsSeparateCompletedCalculationFromPreviewWithoutProgressFractions() {
        JsonObject english = language("en_us");
        JsonObject chinese = language("zh_cn");
        String previewKey = GuideToolMessage.Key.ANALYSIS_PREVIEW.translationKey();
        assertEquals("Execution complete",
                english.get(previewKey).getAsString().formatted("1", "5"));
        assertEquals("执行完成",
                chinese.get(previewKey).getAsString().formatted("1", "5"));
        String fieldsKey = GuideToolMessage.Key.ANALYSIS_FIELDS_PREVIEW.translationKey();
        assertEquals("Execution complete",
                english.get(fieldsKey).getAsString().formatted("5", "5"));
        assertEquals("执行完成",
                chinese.get(fieldsKey).getAsString().formatted("5", "5"));
        for (JsonObject locale : List.of(english, chinese)) {
            assertTrue(!locale.get(previewKey).getAsString().contains("/"));
            assertTrue(!locale.get(GuideToolMessage.Key.ANALYSIS_COMPLETE.translationKey())
                    .getAsString().contains("shown"));
        }
        assertTrue(english.get(GuideToolMessage.Key.ANALYSIS_WORKSPACE.translationKey())
                .getAsString().contains("only while the request is running"));
        assertTrue(chinese.get(GuideToolMessage.Key.ANALYSIS_WORKSPACE.translationKey())
                .getAsString().contains("仅在请求运行期间可用"));
    }

    @Test
    void everyClosedMessageHasEnglishAndSimplifiedChineseTranslations() {
        JsonObject english = language("en_us");
        JsonObject chinese = language("zh_cn");
        assertEquals("Run JavaScript", english.get("screen.openallay.tool.run_javascript").getAsString());
        assertEquals("执行 JavaScript", chinese.get("screen.openallay.tool.run_javascript").getAsString());
        assertEquals("Run the JavaScript for this step.",
                english.get("screen.openallay.tool.intent.run_javascript.description").getAsString());
        assertEquals("执行这一步的 JavaScript。",
                chinese.get("screen.openallay.tool.intent.run_javascript.description").getAsString());
        for (String key : List.of("screen.openallay.tool.run_javascript",
                "screen.openallay.tool.intent.label", "screen.openallay.tool.intent.run_javascript.description")) {
            assertTrue(english.has(key), "missing en_us: " + key);
            assertTrue(chinese.has(key), "missing zh_cn: " + key);
        }
        for (GuideToolMessage.Key key : GuideToolMessage.Key.values()) {
            assertTrue(english.has(key.translationKey()), "missing en_us: " + key.translationKey());
            assertTrue(chinese.has(key.translationKey()), "missing zh_cn: " + key.translationKey());
        }
        for (String suffix : List.of(
                "status", "status.running", "status.succeeded", "status.failed",
                "source", "authority", "provenance", "coverage", "coverage.complete",
                "coverage.partial", "coverage.unknown", "captured_at",
                "source.minecraft.client_player", "source.minecraft.client_registry",
                "source.minecraft.recipe_manager", "source.minecraft.client_recipe_book",
                "source.viewer.jei", "source.viewer.rei", "source.patchouli.resources")) {
            String key = "screen.openallay.detail.tool." + suffix;
            assertTrue(english.has(key), "missing en_us: " + key);
            assertTrue(chinese.has(key), "missing zh_cn: " + key);
        }
    }

    private static JsonObject language(String locale) {
        try (var input = GuideToolPresentationTest.class.getResourceAsStream(
                        "/assets/openallay/lang/" + locale + ".json");
                var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("Unable to load " + locale, exception);
        }
    }
}
