package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void debugResultRetainsRawSourcesAlongsideCanonicalSourceMetadata() {
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
        assertEquals(javascript.normalized(), detail.debug().orElseThrow().normalized());
        assertEquals(List.of(source), javascript.sources());
        assertEquals(42, detail.debug().orElseThrow().normalized().getAsJsonObject("value").get("preview").getAsInt());
        assertTrue(javascript.normalized().getAsJsonObject("value").has("sources"));
        assertTrue(GuideToolDetailPresenter.project(original, true).debug().orElseThrow()
                .normalized().getAsJsonObject("value").has("sources"));
        var unknown = GuideToolDetailPresenter.project(activity("addon:custom", json), true);
        assertTrue(unknown.debug().orElseThrow().normalized().getAsJsonObject("value").has("sources"));
    }

    @Test
    void javascriptResultsUseBoundedStructuredPlayerPreview() {
        var arguments = dev.openallay.json.JsonTrees.parse("""
                {
                  "source":"return mc.items.filter(item => item.id.includes('sword'));",
                  "title":"比较武器",
                  "description":"比较观察到的攻击伤害",
                  "handles":["r_input"]
                }
                """).getAsJsonObject();
        GuideToolActivity activity = new GuideToolActivity(
                "call-secret",
                0,
                "openallay:run_javascript",
                GuideToolStatus.SUCCEEDED,
                arguments,
                dev.openallay.json.JsonTrees.parse("""
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
        assertEquals(dev.openallay.guide.GuideToolInvocationView.none(), view.invocation());
        assertFalse(preview.complete());
        assertEquals(7, preview.omittedRows());
        assertEquals(0, preview.omittedFields());
        assertTrue(view.debug().isEmpty());
        assertFalse(view.toString().contains("r_input"));
        assertFalse(view.toString().contains("openallay:crafting"));
        assertFalse(view.toString().contains("r_secret"));
        assertFalse(view.toString().contains("internal projection"));
        assertFalse(view.toString().contains("return mc.items"));
        assertEquals(
                GuideToolMessage.Key.ANALYSIS_PREVIEW,
                view.narration().getFirst().key());

        GuideToolDetailView debug = GuideToolDetailPresenter.project(activity, true);
        assertEquals(activity.invocation(), debug.invocation());
        assertEquals(List.of("r_input"), debug.invocation().handles());
        assertEquals(List.of("openallay:crafting"), debug.invocation().modules());
        assertEquals(view.intent(), debug.intent());
        assertEquals(view.cards(), debug.cards());
        assertEquals(view.narration(), debug.narration());
        assertEquals(activity.invocationArguments(), debug.debug().orElseThrow().invocationArguments());
        assertEquals(activity.normalized(), debug.debug().orElseThrow().normalized());
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
        GuideDetailCard.Recipe recipeCard = assertInstanceOf(
                GuideDetailCard.Recipe.class, recipe.cards().getFirst());
        assertEquals("minecraft:iron_block", recipeCard.recipe().id());
        assertEquals("minecraft:recipe_manager", recipeCard.recipe().reference().sourceId());
        assertEquals(List.of(new GuideRecipeCard.Output("minecraft:iron_block", 1, "Block of Iron")),
                recipeCard.recipe().outputs());
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_COMPLETE, "1")),
                recipe.narration());
        assertEquals(GuideToolStatus.SUCCEEDED, recipe.status());

        GuideToolDetailView items = GuideToolDetailPresenter.project(activity(
                "openallay:run_javascript", """
                {"status":"success","value":{
                  "resultType":"array","cardinality":2,
                  "fields":["id","displayName"],"viewKind":"ITEM",
                  "preview":[
                    {"id":"minecraft:apple","displayName":"Apple","kind":"item","count":64},
                    {"id":"minecraft:bread","displayName":"Bread","kind":"item","count":2}],
                  "complete":true,"omittedRows":0,"omittedFields":0}}
                """), false);
        GuideDetailCard.ItemGrid grid = assertInstanceOf(
                GuideDetailCard.ItemGrid.class, items.cards().getFirst());
        assertEquals(
                List.of("minecraft:apple", "minecraft:bread"),
                grid.items().stream().map(GuideItemView::itemId).toList());
        assertEquals(List.of(64L, 2L), grid.items().stream().map(GuideItemView::count).toList());
    }

    @Test
    void restoredInvocationFactsRemainAvailableOnlyInTechnicalProjection() {
        var invocation = new dev.openallay.guide.GuideToolInvocationView(
                List.of("r_restored"), List.of("addon:restored"), false);
        var original = activity("openallay:run_javascript", """
                {"status":"success","value":{"resultType":"number","cardinality":1,
                  "viewKind":"SCALAR","preview":42,"complete":true}}
                """);
        var restored = new GuideToolActivity(original.invocationId(), original.index(), original.toolId(),
                original.status(), null, invocation, original.normalized(),
                original.presentationMessages(), original.sources());
        GuideToolDetailView normal = GuideToolDetailPresenter.project(restored, false);
        GuideToolDetailView technical = GuideToolDetailPresenter.project(restored, true);

        assertTrue(normal.invocation().empty());
        assertFalse(normal.invocation().liveArgumentsAvailable());
        assertTrue(normal.debug().isEmpty());
        assertEquals(invocation, technical.invocation());
        assertNull(technical.debug().orElseThrow().invocationArguments());
        assertEquals(normal.cards(), technical.cards());
        assertEquals(normal.narration(), technical.narration());
    }

    @Test
    void technicalFieldNamesInPlayerResultsAreNotScannedOrRemoved() {
        GuideToolDetailView view = GuideToolDetailPresenter.project(activity("openallay:run_javascript", """
                {"status":"success","value":{"resultType":"object","cardinality":2,
                  "viewKind":"KEY_VALUE","complete":true,
                  "preview":{"handles":"player-selected label","modules":"comparison result"}}}
                """), false);
        GuideDetailCard.KeyValue card = assertInstanceOf(GuideDetailCard.KeyValue.class, view.cards().getFirst());
        assertEquals("player-selected label", value(card, "handles"));
        assertEquals("comparison result", value(card, "modules"));
    }

    @Test
    void fullRawValueSurvivesBoundedPlayerPreviewWithoutChangingOriginal() {
        String fullValue = "player result ".repeat(40) + "🧱";
        var normalized = dev.openallay.json.JsonTrees.parse("""
                {"status":"success","value":{"resultType":"string","cardinality":1,
                  "viewKind":"SCALAR","complete":true}}
                """).getAsJsonObject();
        normalized.getAsJsonObject("value").addProperty("preview", fullValue);
        var activity = new GuideToolActivity("long-result", 0, "openallay:run_javascript",
                GuideToolStatus.SUCCEEDED, normalized, List.of(), List.of());
        GuideToolDetailView normal = GuideToolDetailPresenter.project(activity, false);
        GuideToolDetailView technical = GuideToolDetailPresenter.project(activity, true);

        GuideDetailCard.Text preview = assertInstanceOf(GuideDetailCard.Text.class, normal.cards().getFirst());
        assertTrue(preview.lines().getFirst().length() < fullValue.length());
        assertEquals(normal.cards(), technical.cards());
        assertEquals(fullValue, technical.debug().orElseThrow().normalized()
                .getAsJsonObject("value").get("preview").getAsString());
        assertEquals(normalized, activity.normalized());
    }

    @Test
    void keyValueCommandResultShowsStateAndSmallMessageArray() {
        GuideToolDetailView view = GuideToolDetailPresenter.project(activity(
                "openallay:run_javascript", """
                {"status":"success","value":{
                  "resultType":"object","cardinality":2,"viewKind":"KEY_VALUE",
                  "preview":{"state":"completed","messages":[
                    "Set own game mode to Creative Mode",
                    "Made OpenAllay ride a Minecart"]},
                  "complete":true,"omittedRows":0,"omittedFields":0}}
                """), false);

        GuideDetailCard.KeyValue card = assertInstanceOf(
                GuideDetailCard.KeyValue.class, view.cards().getFirst());
        assertEquals("completed", value(card, "state"));
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_FIELDS_COMPLETE, "2")),
                view.narration());
        assertTrue(value(card, "messages").contains("Creative Mode"));
        assertTrue(value(card, "messages").contains("Minecart"));
    }

    @Test
    void everyPreviewShapeKeepsSucceededStatusAndTypeAccurateNarration() {
        record Case(String type, String kind, String preview, GuideToolMessage.Key key,
                String shown, Class<? extends GuideDetailCard> cardType) {}
        List<Case> cases = List.of(
                new Case("array", "TABLE", "[{\"id\":\"a\"}]",
                        GuideToolMessage.Key.ANALYSIS_PREVIEW, "1", GuideDetailCard.Table.class),
                new Case("array", "ITEM", "[{\"id\":\"minecraft:apple\"}]",
                        GuideToolMessage.Key.ANALYSIS_PREVIEW, "1", GuideDetailCard.ItemGrid.class),
                new Case("array", "UNKNOWN", "[1,true,null]",
                        GuideToolMessage.Key.ANALYSIS_PREVIEW, "3", GuideDetailCard.DataPreview.class),
                new Case("array", "UNKNOWN", "[[1,2]]",
                        GuideToolMessage.Key.ANALYSIS_PREVIEW, "1", GuideDetailCard.DataPreview.class),
                new Case("object", "KEY_VALUE", "{\"rows\":[{\"id\":\"a\"}],\"count\":5}",
                        GuideToolMessage.Key.ANALYSIS_FIELDS_PREVIEW, "2", GuideDetailCard.KeyValue.class),
                new Case("object", "UNKNOWN", "{\"rows\":[[1,2]],\"count\":5}",
                        GuideToolMessage.Key.ANALYSIS_FIELDS_PREVIEW, "2", GuideDetailCard.DataPreview.class));
        for (Case test : cases) {
            GuideToolDetailView view = GuideToolDetailPresenter.project(activity(
                    "openallay:run_javascript", """
                    {"status":"success","value":{"resultType":"%s","viewKind":"%s",
                     "cardinality":5,"complete":false,"preview":%s}}
                    """.formatted(test.type(), test.kind(), test.preview())), false);
            assertInstanceOf(test.cardType(), view.cards().getFirst());
            assertEquals(GuideToolStatus.SUCCEEDED, view.status());
            assertEquals(GuideToolDisplayStatus.SUCCEEDED, view.displayStatus());
            assertTrue(view.failure().isEmpty());
            assertEquals(List.of(GuideToolMessage.of(test.key(), test.shown(), "5")), view.narration());
        }
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
                dev.openallay.json.JsonTrees.parse(json).getAsJsonObject(),
                List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED)),
                List.of());
    }
}
