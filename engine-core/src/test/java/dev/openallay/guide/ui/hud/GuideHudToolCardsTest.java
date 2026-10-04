package dev.openallay.guide.ui.hud;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.ui.GuideToolDetailPresenter;
import dev.openallay.guide.ui.GuideUiRow;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideHudToolCardsTest {
    @Test void itemCardsRetainTypedIdsCountsAndStableSemanticNodesAcrossLanguageAndTheme() {
        var tool = tool("""
                {"status":"success","value":{"viewKind":"ITEM","preview":[
                {"itemId":"minecraft:diamond","displayName":"Diamond","count":64},
                {"itemId":"minecraft:oak_log","displayName":"Oak log","count":128}]}}
                """);
        var first = GuideHudToolCards.project(tool, key -> "Items");
        var translated = GuideHudToolCards.project(tool, key -> "物品清单");
        var nodes = first.document().blocks().stream().filter(SemanticBlock.Component.class::isInstance)
                .map(SemanticBlock.Component.class::cast).toList();
        assertEquals(2, nodes.size());
        assertEquals(List.of("minecraft:diamond", "minecraft:oak_log"), nodes.stream()
                .map(node -> ((RichComponent.ItemRow) node.component()).items().getFirst().itemId()).toList());
        assertEquals(List.of(64L, 128L), nodes.stream()
                .map(node -> ((RichComponent.ItemRow) node.component()).items().getFirst().count()).toList());
        assertEquals(first.document().blocks().stream().map(SemanticBlock::nodeId).toList(),
                translated.document().blocks().stream().map(SemanticBlock::nodeId).toList());
        assertTrue(nodes.stream().allMatch(node -> node.nodeId().matches("[0-9a-f]{64}")));
        assertEquals(2, ((dev.openallay.guide.ui.GuideDetailCard.ItemGrid) tool.detail().cards().getFirst()).items().size());
    }
    @Test void tableRowsAndLongValuesRemainTypedInsteadOfThreeLineAnswerSummaries() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("status", "success");
        var value = new com.google.gson.JsonObject();
        value.addProperty("viewKind", "TABLE");
        value.addProperty("complete", true);
        var rows = new com.google.gson.JsonArray();
        for (int i = 0; i < 35; i++) {
            var row = new com.google.gson.JsonObject(); row.addProperty("step", i); row.addProperty("result", "placed blocks " + i); rows.add(row);
        }
        value.add("preview", rows); json.add("value", value);
        var projection = GuideHudToolCards.project(tool(json.toString()), key -> "Result");
        var table = (SemanticBlock.Table) projection.document().blocks().getLast();
        assertEquals(35, table.rows().size());
        assertTrue(projection.document().fallbackText().contains("placed blocks 34"));
    }
    @Test void exactRecipeCardRetainsGenerationOriginAndNativeBindingFields() {
        String generation = "a".repeat(64);
        var tool = tool("""
                {"status":"success","value":{"viewKind":"RECIPE","preview":[{
                  "reference":{"sourceId":"minecraft:recipe_manager","generation":"%s","recipeId":"minecraft:iron_block"},
                  "id":"minecraft:iron_block","type":"minecraft:crafting","workstation":"minecraft:crafting_table",
                  "ingredients":[],"catalysts":[],"byproducts":[],
                  "outputs":[{"stack":{"itemId":"minecraft:iron_block","count":1,"displayName":"Block of Iron"},"probability":1.0}]
                }]}}
                """.formatted(generation));
        var projection = GuideHudToolCards.project(tool, key -> "Recipe");
        var node = (SemanticBlock.Component) projection.document().blocks().getFirst();
        var grid = (RichComponent.RecipeGrid) node.component();
        assertEquals("stable-call", grid.originInvocationId());
        assertEquals(generation, grid.recipe().generation());
        assertEquals("minecraft:iron_block", grid.recipe().recipeId());
        var recipe = projection.recipes().get(grid.nodeId());
        assertEquals(grid.recipe(), recipe.reference());
        assertEquals("minecraft:crafting_table", recipe.workstation());
        assertEquals("minecraft:iron_block", recipe.outputs().getFirst().itemId());
        assertEquals(1, recipe.outputs().getFirst().count());
    }

    private static GuideUiRow.Tool tool(String normalized) {
        var activity = new GuideToolActivity("stable-call", 0, "openallay:run_javascript", GuideToolStatus.SUCCEEDED,
                JsonParser.parseString(normalized).getAsJsonObject(), List.of(), List.of());
        return new GuideUiRow.Tool(UUID.fromString("d5168b68-aaf6-49e9-b071-c29625c51ed1"), 0, activity,
                GuideToolDetailPresenter.project(activity, false).forRequest(true));
    }
}
