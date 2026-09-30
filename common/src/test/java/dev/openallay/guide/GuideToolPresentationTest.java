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
                {"status":"success","value":{"cardinality":9,"complete":false,
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
