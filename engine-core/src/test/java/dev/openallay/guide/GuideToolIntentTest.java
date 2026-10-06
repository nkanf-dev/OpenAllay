package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuideToolIntentTest {
    @Test
    void projectsOnlyActualStringIntentAndSanitizesControlsWithoutInterpretingText() {
        JsonObject arguments = dev.openallay.json.JsonTrees.parse("{\"title\":false,\"description\":{}}").getAsJsonObject();
        assertTrue(GuideToolIntent.fromArguments(arguments).empty());
        arguments.addProperty("title", "  **分析** [[tw:item|minecraft:apple]]\n/openallay test  ");
        arguments.addProperty("description", "screen.openallay.title\t<clickEvent>🧚");
        GuideToolIntent intent = GuideToolIntent.fromArguments(arguments);
        assertEquals("**分析** [[tw:item|minecraft:apple]] /openallay test", intent.title());
        assertEquals("screen.openallay.title <clickEvent>🧚", intent.description());
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT,
                        intent.title(), intent.description())),
                GuideToolInvocationPresentation.messages("openallay:run_javascript", arguments));
    }

    @Test
    void legacyBlankUnsupportedArityAndOtherToolsHaveNoIntent() {
        for (List<String> values : List.of(List.<String>of(), List.of("one"),
                List.of("one", "two", "three"), List.of(" ", ""))) {
            assertTrue(GuideToolIntent.from("openallay:run_javascript", null, List.of(
                    new GuideToolMessage(GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT, values))).empty());
        }
        JsonObject arguments = new JsonObject();
        arguments.addProperty("title", "Not skill intent");
        assertTrue(GuideToolIntent.from("openallay:load_skill", arguments, List.of()).empty());
        assertEquals(GuideToolIntent.none(), GuideToolIntent.fromArguments(null));
    }

    @Test
    void activityDefensivelyCapturesIntentAndNeverReadsResultTitle() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("title", "Compare swords");
        arguments.addProperty("description", "Rank observed attack damage");
        JsonObject normalized = dev.openallay.json.JsonTrees.parse(
                "{\"status\":\"success\",\"value\":{\"title\":\"Overwrite\",\"description\":\"Invent success\"}}")
                .getAsJsonObject();
        GuideToolActivity activity = new GuideToolActivity("call-1", 0, "openallay:run_javascript",
                GuideToolStatus.SUCCEEDED, arguments, normalized,
                GuideToolInvocationPresentation.messages("openallay:run_javascript", arguments), List.of());
        arguments.addProperty("title", "mutated");
        activity.invocationArguments().addProperty("title", "mutated copy");
        normalized.getAsJsonObject("value").addProperty("title", "mutated result");
        assertEquals(new GuideToolIntent("Compare swords", "Rank observed attack damage"), activity.intent());
        assertFalse(activity.intent().title().contains("mutated"));
        GuideToolActivity restored = new GuideToolActivity("call-1", 0, "openallay:run_javascript",
                GuideToolStatus.SUCCEEDED, null, activity.presentationMessages(), List.of());
        assertEquals(activity.intent(), restored.intent());
    }
}
