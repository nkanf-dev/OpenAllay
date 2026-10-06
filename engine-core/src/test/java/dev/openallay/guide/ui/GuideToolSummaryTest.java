package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.openallay.context.RecipeReference;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolIntent;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.guide.GuideToolStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideToolSummaryTest {
    private static final UUID REQUEST = UUID.fromString("590f1b55-4d2f-4b35-9b48-a714428d20ae");
    private static final String TITLE_KEY = "screen.openallay.tool.run_javascript";

    @Test void actualIntentIsLiteralAndToolTitleKeyRemainsTheFallback() {
        var arguments = dev.openallay.json.JsonTrees.parse("""
                {"title":"Check nearby supplies", "description":"Count apples before crafting",
                 "source":"return inventory;"}
                """).getAsJsonObject();
        var tool = tool("intent", GuideToolStatus.SUCCEEDED, arguments, scalarResult(17), true);
        var summary = GuideToolSummaryPresenter.project(tool);
        assertEquals("tool:" + REQUEST + ":intent", summary.id());
        assertEquals("Check nearby supplies", summary.title());
        assertEquals(TITLE_KEY, summary.titleKey());
        assertEquals("Count apples before crafting", summary.description());
        assertTrue(summary.hasDescription());
        assertEquals(GuideToolDisplayStatus.SUCCEEDED, summary.status());

        var withoutIntent = GuideToolSummaryPresenter.project(
                tool("fallback", GuideToolStatus.SUCCEEDED, null, scalarResult(17), true));
        assertEquals("", withoutIntent.title());
        assertEquals(TITLE_KEY, withoutIntent.titleKey());
        assertEquals("", withoutIntent.description());
        assertFalse(withoutIntent.hasDescription());
    }

    @Test void optionalDescriptionIsNotFabricatedFromResultsOrNarration() {
        var arguments = dev.openallay.json.JsonTrees.parse("""
                {"title":"Count supplies", "source":"return { placed: 318 };"}
                """).getAsJsonObject();
        var activity = new GuideToolActivity("no-description", 0, "openallay:run_javascript",
                GuideToolStatus.SUCCEEDED, arguments, dev.openallay.json.JsonTrees.parse("""
                {"status":"success","value":{"resultType":"object","cardinality":1,
                 "viewKind":"KEY_VALUE","preview":{"placed":318},"complete":true,
                 "modelText":"A convincing description that was not requested"}}
                """).getAsJsonObject(),
                List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED)), List.of());
        var tool = new GuideUiRow.Tool(REQUEST, 0, activity,
                GuideToolDetailPresenter.project(activity, false).forRequest(true));
        var summary = GuideToolSummaryPresenter.project(tool);
        assertEquals("Count supplies", summary.title());
        assertEquals("", summary.description());
        assertFalse(summary.hasDescription());
        assertTrue(summary.capsules().isEmpty());
    }

    @Test void actualFailedRunningAndMissingResultStatesNeverBecomeSuccessCapsules() {
        String successfulItems = itemResult();
        for (var status : List.of(GuideToolStatus.FAILED, GuideToolStatus.RUNNING)) {
            var tool = tool("status-" + status, status, null, successfulItems, false);
            assertFalse(tool.detail().cards().isEmpty(), "typed data alone is not execution status");
            var summary = GuideToolSummaryPresenter.project(tool);
            assertEquals(GuideToolDisplayStatus.from(status, false), summary.status());
            assertTrue(summary.capsules().isEmpty());
        }
        var terminalRunning = tool("interrupted", GuideToolStatus.RUNNING, null, successfulItems, true);
        assertFalse(terminalRunning.detail().cards().isEmpty());
        var missing = GuideToolSummaryPresenter.project(terminalRunning);
        assertEquals(GuideToolDisplayStatus.NO_RESULT_RECORDED, missing.status());
        assertTrue(missing.capsules().isEmpty());

        var unrecorded = GuideToolSummaryPresenter.project(
                tool("unrecorded", GuideToolStatus.RUNNING, null, null, true));
        assertEquals(GuideToolDisplayStatus.NO_RESULT_RECORDED, unrecorded.status());
        assertTrue(unrecorded.capsules().isEmpty());
    }

    @Test void actualNormalizedFailureBlocksCapsulesAndKeepsItsCompleteReason() {
        var tool = tool("failed", GuideToolStatus.FAILED, null, """
                {"status":"failure","code":"javascript_error","message":"ReferenceError at line 17"}
                """, true);
        var summary = GuideToolSummaryPresenter.project(tool);
        assertEquals(GuideToolDisplayStatus.FAILED, summary.status());
        assertTrue(summary.capsules().isEmpty());
        assertEquals("javascript_error", tool.detail().failure().orElseThrow().code());
        assertEquals("ReferenceError at line 17", tool.detail().failure().orElseThrow().message());

        var staleCards = toolWithCards("failure-with-cards", List.of(itemGrid()),
                GuideToolDisplayStatus.SUCCEEDED,
                Optional.of(new GuideToolDetailView.Failure("tool_error", "No item was returned")));
        assertTrue(GuideToolSummaryPresenter.project(staleCards).capsules().isEmpty(),
                "a recorded failure wins over stale typed cards");
    }

    @Test void itemGridCapsulesAreCappedAndKeepStableCardAndInvocationOrigins() {
        var tool = tool("items", GuideToolStatus.SUCCEEDED, null, itemResult(), true);
        var allItems = assertInstanceOf(GuideDetailCard.ItemGrid.class, tool.detail().cards().getFirst());
        assertEquals(4, allItems.items().size());
        var summary = GuideToolSummaryPresenter.project(tool);
        assertEquals(3, summary.capsules().size());
        assertEquals(GuideToolSummaryPresenter.MAX_CAPSULES, summary.capsules().size());
        for (int index = 0; index < summary.capsules().size(); index++) {
            var capsule = assertInstanceOf(GuideToolSummaryPresenter.Item.class, summary.capsules().get(index));
            assertEquals("tool:" + REQUEST + ":items:card:0:item:" + index, capsule.id());
            assertEquals("items", capsule.originInvocationId());
            assertEquals(allItems.items().get(index), capsule.item());
        }
        assertEquals(128, summary.capsules().getFirst().item().count());
        assertEquals(summary, GuideToolSummaryPresenter.project(tool));
        assertEquals(4, allItems.items().size(), "summary clipping must not trim full detail");
        var anotherInvocation = GuideToolSummaryPresenter.project(
                tool("other-items", GuideToolStatus.SUCCEEDED, null, itemResult(), true));
        assertNotEquals(summary.capsules().getFirst().id(), anotherInvocation.capsules().getFirst().id());
        var anotherRequest = GuideToolSummaryPresenter.project(
                new GuideUiRow.Tool(UUID.fromString("1caf3304-b7da-4a9d-ab3d-d561f35bdcb1"),
                        0, tool.activity(), tool.detail()));
        assertNotEquals(summary.capsules().getFirst().id(), anotherRequest.capsules().getFirst().id());
    }

    @Test void onlyTypedRecipesAndItemsBecomeCapsulesWithOriginalCardIndexes() {
        var recipe = recipe("minecraft:bread", "minecraft:bread", 3);
        var extraRecipe = recipe("minecraft:apple_recipe", "minecraft:apple", 1);
        List<GuideDetailCard> cards = List.of(
                new GuideDetailCard.Text("screen.openallay.detail.analysis", List.of("minecraft:diamond ×99")),
                new GuideDetailCard.Recipe(recipe),
                new GuideDetailCard.Table("screen.openallay.detail.analysis.table",
                        List.of("item", "count"), List.of(List.of("minecraft:diamond", "99")), true, 0, 0),
                new GuideDetailCard.ItemGrid("screen.openallay.detail.analysis.items", List.of(
                        new GuideItemView("minecraft:apple", "Apple", 128),
                        new GuideItemView("minecraft:bread", "Bread", 64))),
                new GuideDetailCard.Recipe(extraRecipe));
        var tool = toolWithCards("mixed", cards, GuideToolDisplayStatus.SUCCEEDED, Optional.empty());
        var summary = GuideToolSummaryPresenter.project(tool);
        assertEquals(3, summary.capsules().size());
        var capsule = assertInstanceOf(GuideToolSummaryPresenter.Recipe.class, summary.capsules().getFirst());
        assertEquals("tool:" + REQUEST + ":mixed:card:1:recipe", capsule.id());
        assertEquals("mixed", capsule.originInvocationId());
        assertSame(recipe, capsule.recipe());
        assertEquals(new GuideItemView("minecraft:bread", "Bread", 3), capsule.item());
        assertEquals("tool:" + REQUEST + ":mixed:card:3:item:0", summary.capsules().get(1).id());
        assertEquals("tool:" + REQUEST + ":mixed:card:3:item:1", summary.capsules().get(2).id());
        assertEquals(cards, tool.detail().cards(), "non-summary result families remain in full detail");
        assertEquals(extraRecipe, ((GuideDetailCard.Recipe) tool.detail().cards().getLast()).recipe());
    }

    @Test void normalizedRecipeCardsAreCappedWithoutDroppingStoredRecipes() {
        var recipes = new com.google.gson.JsonArray();
        for (int index = 0; index < 4; index++) {
            var recipe = new JsonObject();
            recipe.addProperty("id", "minecraft:bread_" + index);
            recipe.addProperty("type", "minecraft:crafting_shapeless");
            var reference = new JsonObject();
            reference.addProperty("sourceId", "openallay:recipes");
            reference.addProperty("generation", "a".repeat(64));
            reference.addProperty("recipeId", "minecraft:bread_" + index);
            recipe.add("reference", reference);
            var output = new JsonObject();
            var stack = new JsonObject();
            stack.addProperty("itemId", "minecraft:bread");
            stack.addProperty("displayName", "Bread");
            stack.addProperty("count", index + 1);
            output.add("stack", stack);
            var outputs = new com.google.gson.JsonArray();
            outputs.add(output);
            recipe.add("outputs", outputs);
            recipes.add(recipe);
        }
        var value = new JsonObject();
        value.addProperty("viewKind", "RECIPE");
        value.addProperty("resultType", "array");
        value.addProperty("cardinality", 4);
        value.addProperty("complete", true);
        value.add("preview", recipes);
        var normalized = new JsonObject();
        normalized.addProperty("status", "success");
        normalized.add("value", value);
        var tool = tool("recipes", GuideToolStatus.SUCCEEDED, null, normalized.toString(), true);
        var summary = GuideToolSummaryPresenter.project(tool);
        assertEquals(3, summary.capsules().size());
        assertEquals(4, tool.detail().cards().size());
        for (int index = 0; index < summary.capsules().size(); index++) {
            var capsule = assertInstanceOf(GuideToolSummaryPresenter.Recipe.class, summary.capsules().get(index));
            assertEquals("tool:" + REQUEST + ":recipes:card:" + index + ":recipe", capsule.id());
            assertEquals("recipes", capsule.originInvocationId());
            assertEquals(index + 1, capsule.item().count());
            assertSame(((GuideDetailCard.Recipe) tool.detail().cards().get(index)).recipe(), capsule.recipe());
        }
        assertEquals("minecraft:bread_3", ((GuideDetailCard.Recipe) tool.detail().cards().getLast()).recipe().id());
    }

    @Test void recipesWithoutOutputsAreNotInventedAsResultCapsules() {
        var reference = new RecipeReference("openallay:recipes", "a".repeat(64), "minecraft:empty");
        var emptyRecipe = new GuideRecipeCard(reference, List.of(reference),
                "minecraft:empty", "minecraft:crafting_shapeless", "", List.of());
        var tool = toolWithCards("empty-recipe", List.of(new GuideDetailCard.Recipe(emptyRecipe)),
                GuideToolDisplayStatus.SUCCEEDED, Optional.empty());
        assertTrue(GuideToolSummaryPresenter.project(tool).capsules().isEmpty());
        assertSame(emptyRecipe, ((GuideDetailCard.Recipe) tool.detail().cards().getFirst()).recipe());
    }

    @Test void tablesScalarsAndPrivateEnvelopesStayOutOfSummaryAndRetainFullDetail() {
        List<String> results = List.of(scalarResult(17), """
                {"status":"success","value":{"resultType":"object","cardinality":1,
                 "viewKind":"KEY_VALUE","preview":{"itemId":"minecraft:diamond","count":99},
                 "complete":false,"omittedFields":9,"handle":"r_private_workspace",
                 "modelText":"Private envelope text"}}
                """, """
                {"status":"success","value":{"resultType":"array","cardinality":2,
                 "viewKind":"TABLE","preview":[{"item":"minecraft:diamond","count":99},
                 {"item":"minecraft:apple","count":7}],"complete":true}}
                """, """
                {"status":"success","value":{"resultType":"object","cardinality":1,
                 "viewKind":"OBJECT","preview":{"itemId":"minecraft:diamond","count":99},
                 "complete":false,"handle":"r_private_workspace","modelText":"Private envelope text"}}
                """);
        for (int index = 0; index < results.size(); index++) {
            var tool = tool("plain-" + index, GuideToolStatus.SUCCEEDED, null, results.get(index), true);
            var detail = tool.detail();
            var cards = List.copyOf(detail.cards());
            assertFalse(cards.isEmpty());
            var summary = GuideToolSummaryPresenter.project(tool);
            assertTrue(summary.capsules().isEmpty());
            assertEquals("", summary.title());
            assertEquals(TITLE_KEY, summary.titleKey());
            assertEquals("", summary.description());
            assertFalse(summary.toString().contains("r_private_workspace"));
            assertFalse(summary.toString().contains("Private envelope text"));
            assertSame(detail, tool.detail());
            assertEquals(cards, tool.detail().cards());
            assertEquals(dev.openallay.json.JsonTrees.parse(results.get(index)), tool.activity().normalized());
        }
        var scalar = tool("scalar", GuideToolStatus.SUCCEEDED, null, results.getFirst(), true);
        assertEquals(List.of("17"), ((GuideDetailCard.Text) scalar.detail().cards().getFirst()).lines());
        var table = tool("table", GuideToolStatus.SUCCEEDED, null, results.get(2), true);
        assertEquals(2, ((GuideDetailCard.Table) table.detail().cards().getFirst()).rows().size());
    }

    @Test void largeFullResultIsPreservedRatherThanLimitedToSummaryCapsules() {
        var rows = new com.google.gson.JsonArray();
        for (int index = 0; index < 40; index++) {
            var row = new JsonObject();
            row.addProperty("step", index);
            row.addProperty("blocks", index + 100);
            rows.add(row);
        }
        var value = new JsonObject();
        value.addProperty("viewKind", "TABLE");
        value.addProperty("complete", true);
        value.add("preview", rows);
        var normalized = new JsonObject();
        normalized.addProperty("status", "success");
        normalized.add("value", value);
        var tool = tool("large-table", GuideToolStatus.SUCCEEDED, null, normalized.toString(), true);
        assertTrue(GuideToolSummaryPresenter.project(tool).capsules().isEmpty());
        var full = assertInstanceOf(GuideDetailCard.Table.class, tool.detail().cards().getFirst());
        assertEquals(40, full.rows().size());
        assertEquals(List.of("39", "139"), full.rows().getLast());
    }

    @Test void compactGeometryKeepsEveryRectangleInsideAtAllSupportedWidths() {
        for (int width : List.of(40, 90, 180, 320)) {
            for (boolean description : List.of(false, true)) {
                for (int statusWidth : List.of(0, 48, 500)) {
                    var geometry = GuideToolSummaryGeometry.measure(9, 30, width, statusWidth,
                            List.of(22, 45, 110), description, 8);
                    int height = description ? 40 : 28;
                    assertEquals(height, geometry.card().height());
                    assertEquals(height + 8, geometry.rowHeight());
                    assertEquals(description ? 10 : 0, geometry.description().height());
                    var rectangles = new ArrayList<>(List.of(geometry.icon(), geometry.title(),
                            geometry.status(), geometry.description()));
                    rectangles.addAll(geometry.capsules());
                    for (var rectangle : rectangles) assertInside(geometry.card(), rectangle);
                    assertTrue(geometry.icon().right() <= geometry.title().x());
                    assertTrue(geometry.title().right() <= geometry.status().x());
                    int previousRight = geometry.status().right();
                    for (var capsule : geometry.capsules()) {
                        assertTrue(capsule.x() >= previousRight);
                        previousRight = capsule.right();
                    }
                }
            }
        }
    }

    @Test void summaryHeightDoesNotDependOnResultAmountOrCapsuleFit() {
        for (int width : List.of(40, 90, 180, 320)) {
            for (boolean description : List.of(false, true)) {
                var empty = GuideToolSummaryGeometry.measure(0, 0, width, 70, List.of(), description, 5);
                var one = GuideToolSummaryGeometry.measure(0, 0, width, 70, List.of(22), description, 5);
                var full = GuideToolSummaryGeometry.measure(0, 0, width, 70, List.of(110, 110, 110), description, 5);
                assertEquals(empty.rowHeight(), one.rowHeight());
                assertEquals(empty.rowHeight(), full.rowHeight());
                assertEquals((description ? 40 : 28) + 5, full.rowHeight());
            }
        }
    }

    @Test void nativeChildHitsWinOverTheirOverlappingCardAndBlankCardOpensDetail() {
        var geometry = GuideToolSummaryGeometry.measure(9, 30, 320, 50, List.of(22, 35), true, 8);
        assertEquals(2, geometry.capsules().size());
        for (int index = 0; index < geometry.capsules().size(); index++) {
            var capsule = geometry.capsules().get(index);
            double x = capsule.x() + capsule.width() / 2.0;
            double y = capsule.y() + capsule.height() / 2.0;
            assertTrue(geometry.card().contains(x, y), "native child overlaps whole-card detail target");
            assertEquals(new GuideToolSummaryGeometry.Hit(GuideToolSummaryGeometry.Kind.CAPSULE, index),
                    geometry.hit(x, y));
        }
        assertEquals(new GuideToolSummaryGeometry.Hit(GuideToolSummaryGeometry.Kind.DETAIL, -1),
                geometry.hit(geometry.card().x() + 2, geometry.card().bottom() - 1));
        assertEquals(new GuideToolSummaryGeometry.Hit(GuideToolSummaryGeometry.Kind.OUTSIDE, -1),
                geometry.hit(geometry.card().x() + 2, geometry.card().bottom() + 1));
        assertEquals(GuideToolSummaryGeometry.Kind.OUTSIDE,
                geometry.hit(geometry.card().right(), geometry.card().y()).kind());
    }

    private static void assertInside(GuideUiLayout.Rect card, GuideUiLayout.Rect child) {
        assertTrue(child.width() >= 0 && child.height() >= 0, child::toString);
        assertTrue(child.x() >= card.x() && child.y() >= card.y(), child::toString);
        assertTrue(child.right() <= card.right() && child.bottom() <= card.bottom(), child::toString);
    }

    private static GuideDetailCard.ItemGrid itemGrid() {
        return new GuideDetailCard.ItemGrid("screen.openallay.detail.analysis.items",
                List.of(new GuideItemView("minecraft:apple", "Apple", 128)));
    }

    private static GuideRecipeCard recipe(String id, String itemId, int count) {
        var reference = new RecipeReference("openallay:recipes", "a".repeat(64), id);
        return new GuideRecipeCard(reference, List.of(reference), id,
                "minecraft:crafting_shapeless", "minecraft:crafting_table",
                List.of(new GuideRecipeCard.Output(itemId, count,
                        itemId.equals("minecraft:bread") ? "Bread" : "Apple")));
    }

    private static GuideUiRow.Tool toolWithCards(String id, List<GuideDetailCard> cards,
            GuideToolDisplayStatus displayStatus, Optional<GuideToolDetailView.Failure> failure) {
        var activity = new GuideToolActivity(id, 0, "openallay:run_javascript",
                GuideToolStatus.SUCCEEDED, null, List.of(), List.of());
        var detail = new GuideToolDetailView(TITLE_KEY, activity.status(), activity.invocation(),
                GuideToolIntent.none(), cards, List.of(), Optional.empty(), displayStatus, failure);
        return new GuideUiRow.Tool(REQUEST, 0, activity, detail);
    }

    private static GuideUiRow.Tool tool(String id, GuideToolStatus status, JsonObject arguments,
            String normalized, boolean terminal) {
        var activity = new GuideToolActivity(id, 0, "openallay:run_javascript", status,
                arguments, normalized == null ? null : dev.openallay.json.JsonTrees.parse(normalized).getAsJsonObject(),
                List.of(), List.of());
        return new GuideUiRow.Tool(REQUEST, 0, activity,
                GuideToolDetailPresenter.project(activity, false).forRequest(terminal));
    }

    private static String scalarResult(int number) {
        return """
                {"status":"success","value":{"resultType":"number","cardinality":1,
                 "viewKind":"SCALAR","preview":%s,"complete":true}}
                """.formatted(number);
    }

    private static String itemResult() {
        return """
                {"status":"success","value":{"resultType":"array","cardinality":4,
                 "viewKind":"ITEM","preview":[{"id":"minecraft:apple","displayName":"Apple","count":128},
                 {"id":"minecraft:bread","displayName":"Bread","count":64},
                 {"id":"minecraft:carrot","displayName":"Carrot","count":32},
                 {"id":"minecraft:potato","displayName":"Potato","count":16}],"complete":true}}
                """;
    }
}
