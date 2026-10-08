package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.context.RecipeReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuideRecipeDetailFactsTest {
    @Test
    void preservesEveryOutputAlternativeResolvedItemByproductAndExactProcessingValue() {
        var outputs = List.of(
                new GuideRecipeCard.Output("minecraft:iron_block", 2, "Iron Block"),
                new GuideRecipeCard.Output("minecraft:gold_block", 3, "Gold Block"),
                new GuideRecipeCard.Output("minecraft:diamond_block", 4, "Diamond Block"),
                new GuideRecipeCard.Output("minecraft:emerald_block", Integer.MAX_VALUE, "Emerald Block"));
        var ingredients = List.of(new GuideRecipeCard.Ingredient("metal", Long.MAX_VALUE, true, List.of(
                new GuideRecipeCard.Alternative("tag", "minecraft:ingots", List.of(
                        "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot")),
                new GuideRecipeCard.Alternative("item", "minecraft:diamond", List.of("minecraft:diamond")),
                new GuideRecipeCard.Alternative("tag", "test:unresolved", List.of()))));
        var catalysts = List.of(new GuideRecipeCard.Ingredient("tool", 9_007_199_254_740_993L, false, List.of(
                new GuideRecipeCard.Alternative("item", "minecraft:hammer", List.of(
                        "test:iron_hammer", "test:gold_hammer")),
                new GuideRecipeCard.Alternative("tag", "test:tools", List.of("test:diamond_hammer")))));
        var byproducts = List.of(
                new GuideRecipeCard.Output("test:slag", 1, "Slag"),
                new GuideRecipeCard.Output("test:dust", 2, "Dust"),
                new GuideRecipeCard.Output("test:ash", 3, "Ash"),
                new GuideRecipeCard.Output("test:scrap", Integer.MAX_VALUE, "Scrap"));
        var processing = new GuideRecipeCard.Processing(Long.MAX_VALUE, 9_007_199_254_740_993L, 1234.125);
        var recipe = recipe("minecraft:crafting_table", outputs, ingredients, catalysts, byproducts, processing);

        var facts = GuideRecipeDetailFacts.project(recipe);

        assertEquals(List.of(
                line("screen.openallay.recipe.identity", "test:recipe", "test:processing"),
                line("screen.openallay.recipe.workstation", "minecraft:crafting_table"),
                line("screen.openallay.recipe.output", "Iron Block", "minecraft:iron_block", "2"),
                line("screen.openallay.recipe.output", "Gold Block", "minecraft:gold_block", "3"),
                line("screen.openallay.recipe.output", "Diamond Block", "minecraft:diamond_block", "4"),
                line("screen.openallay.recipe.output", "Emerald Block", "minecraft:emerald_block", "2147483647"),
                line("screen.openallay.recipe.ingredient", "metal", "9223372036854775807"),
                line("screen.openallay.recipe.alternative", "tag", "minecraft:ingots"),
                line("screen.openallay.recipe.resolved_item", "minecraft:iron_ingot"),
                line("screen.openallay.recipe.resolved_item", "minecraft:gold_ingot"),
                line("screen.openallay.recipe.resolved_item", "minecraft:copper_ingot"),
                line("screen.openallay.recipe.alternative", "item", "minecraft:diamond"),
                line("screen.openallay.recipe.resolved_item", "minecraft:diamond"),
                line("screen.openallay.recipe.alternative", "tag", "test:unresolved"),
                line("screen.openallay.recipe.catalyst", "tool", "9007199254740993"),
                line("screen.openallay.recipe.alternative", "item", "minecraft:hammer"),
                line("screen.openallay.recipe.resolved_item", "test:iron_hammer"),
                line("screen.openallay.recipe.resolved_item", "test:gold_hammer"),
                line("screen.openallay.recipe.alternative", "tag", "test:tools"),
                line("screen.openallay.recipe.resolved_item", "test:diamond_hammer"),
                line("screen.openallay.recipe.byproduct", "Slag", "test:slag", "1"),
                line("screen.openallay.recipe.byproduct", "Dust", "test:dust", "2"),
                line("screen.openallay.recipe.byproduct", "Ash", "test:ash", "3"),
                line("screen.openallay.recipe.byproduct", "Scrap", "test:scrap", "2147483647"),
                line("screen.openallay.native.recipe.duration", "9223372036854775807"),
                line("screen.openallay.native.recipe.energy", "9007199254740993"),
                line("screen.openallay.native.recipe.temperature", "1234.125")), facts);
        assertEquals(Long.MAX_VALUE, recipe.ingredients().getFirst().count());
        assertEquals(9_007_199_254_740_993L, recipe.catalysts().getFirst().count());
        assertSame(processing, recipe.processing());
        assertEquals(4, recipe.outputs().size());
        assertEquals(4, recipe.byproducts().size());
    }

    @Test
    void completeDetailFactsHaveNoCanvasOrCapsuleCollectionLimit() {
        List<GuideRecipeCard.Output> outputs = new ArrayList<>();
        List<GuideRecipeCard.Ingredient> ingredients = new ArrayList<>();
        List<GuideRecipeCard.Ingredient> catalysts = new ArrayList<>();
        List<GuideRecipeCard.Output> byproducts = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            outputs.add(new GuideRecipeCard.Output("test:output_" + index, index + 1, "Output " + index));
            ingredients.add(input("input", index, true));
            catalysts.add(input("catalyst", index, false));
            byproducts.add(new GuideRecipeCard.Output("test:byproduct_" + index, index + 1, "Byproduct " + index));
        }
        var recipe = recipe("", outputs, ingredients, catalysts, byproducts, GuideRecipeCard.Processing.unknown());

        var facts = GuideRecipeDetailFacts.project(recipe);

        assertEquals(1025, facts.size());
        assertEquals(64, facts.stream().filter(value -> value.key().equals("screen.openallay.recipe.output")).count());
        assertEquals(64, facts.stream().filter(value -> value.key().equals("screen.openallay.recipe.ingredient")).count());
        assertEquals(64, facts.stream().filter(value -> value.key().equals("screen.openallay.recipe.catalyst")).count());
        assertEquals(256, facts.stream().filter(value -> value.key().equals("screen.openallay.recipe.alternative")).count());
        assertEquals(512, facts.stream().filter(value -> value.key().equals("screen.openallay.recipe.resolved_item")).count());
        assertEquals(64, facts.stream().filter(value -> value.key().equals("screen.openallay.recipe.byproduct")).count());
        for (int index = 0; index < 64; index++) {
            assertTrue(facts.contains(line("screen.openallay.recipe.output", "Output " + index,
                    "test:output_" + index, Integer.toString(index + 1))));
            assertTrue(facts.contains(line("screen.openallay.recipe.byproduct", "Byproduct " + index,
                    "test:byproduct_" + index, Integer.toString(index + 1))));
            for (String family : List.of("input", "catalyst")) {
                String key = family.equals("input") ? "screen.openallay.recipe.ingredient"
                        : "screen.openallay.recipe.catalyst";
                assertTrue(facts.contains(line(key, family + "-" + index, Long.toString(Long.MAX_VALUE - index))));
                for (int alternative = 0; alternative < 2; alternative++) {
                    String id = "test:" + family + "_" + index + "_" + alternative;
                    assertTrue(facts.contains(line("screen.openallay.recipe.alternative", "tag", id)));
                    assertTrue(facts.contains(line("screen.openallay.recipe.resolved_item", id + "_first")));
                    assertTrue(facts.contains(line("screen.openallay.recipe.resolved_item", id + "_last")));
                }
            }
        }
        assertEquals(line("screen.openallay.recipe.byproduct", "Byproduct 63", "test:byproduct_63", "64"),
                facts.getLast());
        assertEquals(64, recipe.outputs().size());
        assertEquals(64, recipe.ingredients().size());
        assertEquals(64, recipe.catalysts().size());
        assertEquals(64, recipe.byproducts().size());
    }

    @Test
    void absentFactsStayAbsentAndRecordedZeroProcessingValuesStayExact() {
        var empty = recipe("", List.of(), List.of(), List.of(), List.of(), GuideRecipeCard.Processing.unknown());
        assertEquals(List.of(line("screen.openallay.recipe.identity", "test:recipe", "test:processing")),
                GuideRecipeDetailFacts.project(empty));
        var zero = recipe("", List.of(), List.of(), List.of(), List.of(),
                new GuideRecipeCard.Processing(0L, 0L, 0.0));
        assertEquals(List.of(
                line("screen.openallay.recipe.identity", "test:recipe", "test:processing"),
                line("screen.openallay.native.recipe.duration", "0"),
                line("screen.openallay.native.recipe.energy", "0"),
                line("screen.openallay.native.recipe.temperature", "0.0")), GuideRecipeDetailFacts.project(zero));
    }

    @Test
    void projectionAndLineArgumentsAreImmutableAndDoNotChangeTheStoredRecipe() {
        var recipe = recipe("test:machine", List.of(new GuideRecipeCard.Output("test:output", 1, "Output")),
                List.of(input("input", 0, true)), List.of(), List.of(), GuideRecipeCard.Processing.unknown());
        var facts = GuideRecipeDetailFacts.project(recipe);
        assertEquals(facts, GuideRecipeDetailFacts.project(recipe));
        assertThrows(UnsupportedOperationException.class, () -> facts.clear());
        assertThrows(UnsupportedOperationException.class, () -> facts.getFirst().arguments().clear());
        List<String> mutable = new ArrayList<>(List.of("original"));
        var detached = new GuideRecipeDetailFacts.Line("test.key", mutable);
        mutable.set(0, "changed");
        assertEquals(List.of("original"), detached.arguments());
        assertEquals(2, recipe.ingredients().getFirst().alternatives().size());
        assertEquals(List.of("test:input_0_1_first", "test:input_0_1_last"),
                recipe.ingredients().getFirst().alternatives().getLast().resolvedItems());
    }

    @Test
    void boundedNativeCanvasKeepsTheCompleteFactListInTheToolDetailDrawerOnly() throws Exception {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path root = current.getFileName() != null && current.getFileName().toString().equals("common")
                ? current.getParent() : current;
        String nativeCanvas = Files.readString(root.resolve(
                "common/src/main/java/dev/openallay/client/gui/nativeview/GenericRecipeNativeViewProvider.java"));
        String screen = Files.readString(root.resolve(
                "common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java"));
        String summary = Files.readString(root.resolve(
                "engine-core/src/main/java/dev/openallay/guide/ui/GuideToolSummaryPresenter.java"));
        assertTrue(nativeCanvas.contains("Math.min(inputs.size(), columns * 3)"));
        assertTrue(nativeCanvas.contains("Math.min(3, recipe.outputs().size())"));
        assertTrue(nativeCanvas.contains("Math.min(Integer.MAX_VALUE, Math.max(1, count))"));
        assertFalse(nativeCanvas.contains("GuideRecipeDetailFacts"));
        assertFalse(summary.contains("GuideRecipeDetailFacts"));
        int cardStart = screen.indexOf("private int recipeCard(");
        int cardEnd = screen.indexOf("private int recipeAction(", cardStart);
        assertTrue(cardStart >= 0 && cardEnd > cardStart);
        String drawerCard = screen.substring(cardStart, cardEnd);
        assertTrue(drawerCard.contains("int canvasHeight = 126;"));
        assertTrue(drawerCard.contains("y += canvasHeight + 5;"));
        String factLoop = "for (dev.openallay.guide.ui.GuideRecipeDetailFacts.Line line : dev.openallay.guide.ui.GuideRecipeDetailFacts.project(card))";
        assertTrue(drawerCard.contains(factLoop));
        assertTrue(drawerCard.indexOf(factLoop) > drawerCard.indexOf("y += canvasHeight + 5;"));
        assertTrue(drawerCard.contains("y = detailLine(graphics, MinecraftComponents.translatable(line.key(), arguments), detail, y);"));
        assertEquals(1, screen.split("GuideRecipeDetailFacts\\.project", -1).length - 1);
    }

    private static GuideRecipeCard recipe(String workstation, List<GuideRecipeCard.Output> outputs,
            List<GuideRecipeCard.Ingredient> ingredients, List<GuideRecipeCard.Ingredient> catalysts,
            List<GuideRecipeCard.Output> byproducts, GuideRecipeCard.Processing processing) {
        var reference = new RecipeReference("minecraft:recipe_manager", "0".repeat(64), "test:recipe");
        return new GuideRecipeCard(reference, List.of(reference), "test:recipe", "test:processing", workstation,
                outputs, ingredients, catalysts, byproducts, processing);
    }

    private static GuideRecipeCard.Ingredient input(String family, int index, boolean consumed) {
        List<GuideRecipeCard.Alternative> alternatives = new ArrayList<>();
        for (int alternative = 0; alternative < 2; alternative++) {
            String id = "test:" + family + "_" + index + "_" + alternative;
            alternatives.add(new GuideRecipeCard.Alternative("tag", id, List.of(id + "_first", id + "_last")));
        }
        return new GuideRecipeCard.Ingredient(family + "-" + index, Long.MAX_VALUE - index, consumed, alternatives);
    }

    private static GuideRecipeDetailFacts.Line line(String key, String... arguments) {
        return new GuideRecipeDetailFacts.Line(key, List.of(arguments));
    }
}
