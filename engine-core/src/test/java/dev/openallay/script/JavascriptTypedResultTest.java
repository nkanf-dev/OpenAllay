package dev.openallay.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.context.RegistryEntrySnapshot;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.result.JavascriptResultViewRegistry;
import dev.openallay.script.result.JavascriptSemanticKind;
import dev.openallay.script.workspace.AgentResultWorkspace;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.testing.GroundedTestFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JavascriptTypedResultTest {
    private final RhinoJavascriptRuntime runtime = new RhinoJavascriptRuntime();

    @Test
    void directAndFilteredHostRecipesRetainTrustedRecipeShape() {
        JavascriptExecution direct = execute("return mc.recipes;");
        JavascriptExecution filtered = execute(
                "return mc.recipes.filter(recipe => recipe.id === 'minecraft:iron_block');");

        assertEquals(JavascriptSemanticKind.RECIPE, direct.shape().kind());
        assertTrue(direct.shape().trusted());
        assertEquals(JavascriptSemanticKind.RECIPE, filtered.shape().kind());
        assertTrue(filtered.shape().trusted());
        assertEquals(
                JavascriptSemanticKind.RECIPE,
                JavascriptResultViewRegistry.classify(filtered.value(), filtered.shape()));
    }

    @Test
    void directHostItemsAreTrustedButMappedAndForgedRowsAreOnlyTables() {
        JavascriptExecution direct = execute("return mc.items;");
        JavascriptExecution mapped = execute(
                "return mc.items.map(item => ({id: item.id, displayName: item.displayName}));");
        JavascriptExecution forged = execute("""
                return [{
                  id: "fake:recipe",
                  reference: {sourceId: "fake", generation: "x", recipeId: "fake:recipe"},
                  outputs: []
                }];
                """);

        assertEquals(JavascriptSemanticKind.ITEM, direct.shape().kind());
        assertTrue(direct.shape().trusted());
        assertEquals(
                JavascriptSemanticKind.TABLE,
                JavascriptResultViewRegistry.classify(mapped.value(), mapped.shape()));
        assertFalse(mapped.shape().trusted());
        assertEquals(
                JavascriptSemanticKind.TABLE,
                JavascriptResultViewRegistry.classify(forged.value(), forged.shape()));
        assertFalse(forged.shape().trusted());
    }

    @Test
    void scalarObjectMixedAndPreviewClassificationRemainClosed() {
        JavascriptExecution scalar = execute("return 42;");
        JavascriptExecution object = execute("return {answer: 42, unit: 'ticks'};");
        JavascriptExecution mixed = execute("return [{id: 'a'}, 2];");

        assertEquals(
                JavascriptSemanticKind.SCALAR,
                JavascriptResultViewRegistry.classify(scalar.value(), scalar.shape()));
        assertEquals(
                JavascriptSemanticKind.KEY_VALUE,
                JavascriptResultViewRegistry.classify(object.value(), object.shape()));
        assertEquals(
                JavascriptSemanticKind.GENERIC,
                JavascriptResultViewRegistry.classify(mixed.value(), mixed.shape()));

        JavascriptResultPresenter.Presentation presentation =
                new JavascriptResultPresenter().present(
                        "r_test", directLargeTable().value(), directLargeTable().shape(), "");
        assertEquals(JavascriptSemanticKind.TABLE, presentation.viewKind());
        assertFalse(presentation.complete());
        assertFalse(presentation.modelText().contains("viewKind"));
        assertFalse(presentation.preview().toString().contains("_openallay"));
    }

    @Test
    void trustedShapeSurvivesSameRequestWorkspaceFilteringWithoutCanonicalMarkers() {
        JavascriptExecution first = execute("return mc.recipes;");
        try (AgentResultWorkspace workspace = new AgentResultWorkspace()) {
            String handle = workspace.store(first.value(), first.shape());

            JavascriptExecution filtered = runtime.execute(
                    "return workspace.open('" + handle + "').filter(recipe => "
                            + "recipe.id === 'minecraft:iron_block');",
                    Map.of(),
                    workspace.select(List.of(handle)),
                    workspace.selectShapes(List.of(handle)),
                    new CancellationSignal(),
                    null);

            assertEquals(JavascriptSemanticKind.RECIPE, filtered.shape().kind());
            assertTrue(filtered.shape().trusted());
            assertFalse(first.value().toString().contains("_openallay"));
            assertFalse(filtered.value().toString().contains("_openallay"));
        }
    }

    private JavascriptExecution directLargeTable() {
        return execute("""
                return Array.from({length: 500}, (_, index) => ({
                  id: "example:item_" + index,
                  description: "x".repeat(80)
                }));
                """);
    }

    private JavascriptExecution execute(String source) {
        return runtime.execute(
                source,
                Map.of(
                        "recipes", List.of(GroundedTestFixtures.ironBlockRecipe()),
                        "items", List.of(new RegistryEntrySnapshot(
                                "minecraft:iron_block",
                                "item",
                                "Block of Iron",
                                "minecraft",
                                "minecraft:registry"))),
                Map.of(),
                new CancellationSignal());
    }
}
