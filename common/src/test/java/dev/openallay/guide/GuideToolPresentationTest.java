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
        for (GuideToolMessage.Key key : GuideToolMessage.Key.values()) {
            assertTrue(english.has(key.translationKey()), "missing en_us: " + key.translationKey());
            assertTrue(chinese.has(key.translationKey()), "missing zh_cn: " + key.translationKey());
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
