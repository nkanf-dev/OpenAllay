package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.guide.GuideToolStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuideToolDetailPresenterTest {
    @Test
    void actualFailuresRemainVisibleAndGenericToolsNeverShowRawJson() {
        GuideToolDetailView failure = GuideToolDetailPresenter.project(activity(
                "openallay:run_javascript",
                "{\"status\":\"failure\",\"code\":\"stale_reference\",\"message\":\"generation deadbeef\"}"),
                false);
        assertTrue(failure.cards().isEmpty());
        assertEquals("stale_reference", failure.failure().orElseThrow().code());
        assertEquals("generation deadbeef", failure.failure().orElseThrow().message());
        assertEquals(
                List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.FAILURE_STALE_REFERENCE)),
                failure.narration());

        GuideToolDetailView generic = GuideToolDetailPresenter.project(activity(
                "openallay:future_tool",
                "{\"status\":\"success\",\"value\":{\"secretInternalId\":\"abc\"}}"), false);
        String visible = generic.cards() + " " + generic.narration();
        assertFalse(visible.contains("secretInternalId"));
        assertFalse(visible.contains("abc"));
        assertEquals("screen.openallay.tool.result", generic.titleKey());
        GuideToolDetailView debug = GuideToolDetailPresenter.project(activity(
                "openallay:future_tool",
                "{\"status\":\"success\",\"value\":{\"secretInternalId\":\"abc\"}}"), true);
        assertEquals("abc", debug.debug().orElseThrow().normalized().getAsJsonObject("value")
                .get("secretInternalId").getAsString());
    }

    @Test
    void debugResultDoesNotDuplicateCanonicalSourceMetadataOrAlterUnknownToolFields() {
        String json = """
                {"status":"success","value":{"resultType":"number","cardinality":1,
                  "viewKind":"SCALAR","preview":42,"complete":true,
                  "sources":[{"evidence":{"sourceId":"minecraft:client_blocks"},"lastCapturedAt":"1970-01-01T00:00:12Z"}]}}
                """;
        GuideToolActivity original = activity("openallay:run_javascript", json);
        var source = new dev.openallay.guide.GuideSource("openallay:run_javascript",
                new dev.openallay.context.EvidenceMetadata(
                        dev.openallay.context.DataAuthority.CLIENT_VISIBLE,
                        dev.openallay.context.DataCompleteness.COMPLETE, java.time.Instant.EPOCH,
                        "minecraft:client_blocks", "minecraft:captured", "26.2", "fabric", java.util.Map.of()),
                java.time.Instant.EPOCH.plusSeconds(12));
        GuideToolActivity javascript = new GuideToolActivity(original.invocationId(), 0, original.toolId(),
                original.status(), original.normalized(), original.presentationMessages(), List.of(source));
        var detail = GuideToolDetailPresenter.project(javascript, true);
        assertFalse(detail.debug().orElseThrow().normalized().getAsJsonObject("value").has("sources"));
        assertEquals(42, detail.debug().orElseThrow().normalized().getAsJsonObject("value").get("preview").getAsInt());
        assertTrue(javascript.normalized().getAsJsonObject("value").has("sources"));
        assertTrue(GuideToolDetailPresenter.project(original, true).debug().orElseThrow()
                .normalized().getAsJsonObject("value").has("sources"));
        var unknown = GuideToolDetailPresenter.project(activity("addon:custom", json), true);
        assertTrue(unknown.debug().orElseThrow().normalized().getAsJsonObject("value").has("sources"));
    }

    @Test
    void javascriptResultsUseBoundedStructuredPlayerPreview() {
        var arguments = JsonParser.parseString("""
                {
                  "source":"return mc.items.filter(item => item.id.includes('sword'));",
                  "title":"比较武器",
                  "description":"比较观察到的攻击伤害",
                  "handles":[]
                }
                """).getAsJsonObject();
        GuideToolActivity activity = new GuideToolActivity(
                "call-secret",
                0,
                "openallay:run_javascript",
                GuideToolStatus.SUCCEEDED,
                arguments,
                JsonParser.parseString("""
                {"status":"success","value":{
                  "handle":"r_secret","resultType":"array","cardinality":9,
                  "fields":["damage","itemId"],
                  "preview":[
                    {"itemId":"example:obsidian_sword","damage":14},
                    {"itemId":"minecraft:diamond_sword","damage":7}
                  ],
                  "modelText":"internal projection","viewKind":"TABLE","complete":false,
                  "omittedRows":7,"omittedFields":0,"elapsedMillis":12,
                  "modules":["openallay:crafting"],
                  "sources":[]}}
                """).getAsJsonObject(),
                List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED)),
                List.of());
        GuideToolDetailView view = GuideToolDetailPresenter.project(activity, false);

        GuideDetailCard.Table preview = assertInstanceOf(
                GuideDetailCard.Table.class, view.cards().getFirst());
        assertEquals(List.of("damage", "itemId"), preview.columns());
        assertEquals(2, preview.rows().size());
        assertEquals(
                "example:obsidian_sword",
                preview.rows().getFirst().get(preview.columns().indexOf("itemId")));
        assertEquals("比较武器", view.intent().title());
        assertEquals("比较观察到的攻击伤害", view.intent().description());
        assertEquals(List.of("openallay:crafting"), view.invocation().modules());
        assertFalse(view.toString().contains("r_secret"));
        assertFalse(view.toString().contains("internal projection"));
        assertFalse(view.toString().contains("return mc.items"));
        assertEquals(
                GuideToolMessage.Key.ANALYSIS_PREVIEW,
                view.narration().getFirst().key());

        GuideToolDetailView debug = GuideToolDetailPresenter.project(activity, true);
        assertEquals(
                "return mc.items.filter(item => item.id.includes('sword'));",
                debug.debug().orElseThrow().invocationArguments().get("source").getAsString());
    }

    @Test
    void trustedJavascriptRecipeAndItemViewsUseNativeCards() {
        GuideToolDetailView recipe = GuideToolDetailPresenter.project(activity(
                "openallay:run_javascript", """
                {"status":"success","value":{
                  "resultType":"array","cardinality":1,
                  "fields":["id","outputs"],"viewKind":"RECIPE",
                  "preview":[{
                    "reference":{"sourceId":"minecraft:recipe_manager",
                      "generation":"%s","recipeId":"minecraft:iron_block"},
                    "id":"minecraft:iron_block","type":"minecraft:crafting",
                    "workstation":"minecraft:crafting_table",
                    "ingredients":[],"catalysts":[],"byproducts":[],
                    "outputs":[{"stack":{"itemId":"minecraft:iron_block","count":1,
                      "displayName":"Block of Iron"},"probability":1.0}]
                  }],
                  "complete":true,"omittedRows":0,"omittedFields":0}}
                """.formatted("0".repeat(64))), false);
        assertInstanceOf(GuideDetailCard.Recipe.class, recipe.cards().getFirst());

        GuideToolDetailView items = GuideToolDetailPresenter.project(activity(
                "openallay:run_javascript", """
                {"status":"success","value":{
                  "resultType":"array","cardinality":2,
                  "fields":["id","displayName"],"viewKind":"ITEM",
                  "preview":[
                    {"id":"minecraft:apple","displayName":"Apple","kind":"item"},
                    {"id":"minecraft:bread","displayName":"Bread","kind":"item"}],
                  "complete":true,"omittedRows":0,"omittedFields":0}}
                """), false);
        GuideDetailCard.ItemGrid grid = assertInstanceOf(
                GuideDetailCard.ItemGrid.class, items.cards().getFirst());
        assertEquals(
                List.of("minecraft:apple", "minecraft:bread"),
                grid.items().stream().map(GuideItemView::itemId).toList());
    }

    @Test
    void keyValueCommandResultShowsStateAndSmallMessageArray() {
        GuideToolDetailView view = GuideToolDetailPresenter.project(activity(
                "openallay:run_javascript", """
                {"status":"success","value":{
                  "resultType":"object","cardinality":1,"viewKind":"KEY_VALUE",
                  "preview":{"state":"completed","messages":[
                    "Set own game mode to Creative Mode",
                    "Made OpenAllay ride a Minecart"]},
                  "complete":true,"omittedRows":0,"omittedFields":0}}
                """), false);

        GuideDetailCard.KeyValue card = assertInstanceOf(
                GuideDetailCard.KeyValue.class, view.cards().getFirst());
        assertEquals("completed", value(card, "state"));
        assertTrue(value(card, "messages").contains("Creative Mode"));
        assertTrue(value(card, "messages").contains("Minecart"));
    }

    private static String value(GuideDetailCard.KeyValue card, String key) {
        return card.entries().stream()
                .filter(entry -> entry.key().equals(key))
                .findFirst()
                .orElseThrow()
                .value();
    }

    private static GuideToolActivity activity(String toolId, String json) {
        return new GuideToolActivity(
                "call-secret",
                0,
                toolId,
                GuideToolStatus.SUCCEEDED,
                JsonParser.parseString(json).getAsJsonObject(),
                List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED)),
                List.of());
    }
}
