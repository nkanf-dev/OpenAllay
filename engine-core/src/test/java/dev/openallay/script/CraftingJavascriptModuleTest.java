package dev.openallay.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.openallay.crafting.CraftabilityCalculator;
import dev.openallay.model.CancellationSignal;
import dev.openallay.testing.GroundedTestFixtures;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CraftingJavascriptModuleTest {
    @Test
    void matchesCanonicalAllocationForOverlappingAlternatives() {
        var recipe = GroundedTestFixtures.overlappingAlternativeRecipe();
        var inventory = GroundedTestFixtures.inventory(Map.of(
                "minecraft:oak_planks", 1L,
                "minecraft:birch_planks", 1L));
        var expected = new CraftabilityCalculator().calculate(recipe, inventory, 1);

        var execution = new RhinoJavascriptRuntime().execute(
                """
                const crafting = require("openallay:crafting");
                return crafting.allocate(mc.recipe, mc.inventory, 1);
                """,
                Map.of("recipe", recipe, "inventory", inventory),
                Map.of(),
                new CancellationSignal());
        var actual = execution.value().getAsJsonObject();

        assertEquals(expected.craftable(), actual.get("craftable").getAsBoolean());
        assertEquals(expected.conclusive(), actual.get("conclusive").getAsBoolean());
        assertEquals(expected.maximumCrafts(), actual.get("maximumCrafts").getAsLong());
        assertEquals(expected.allocations().size(), actual.getAsJsonArray("allocations").size());
        assertEquals(expected.missing().size(), actual.getAsJsonArray("missing").size());
        assertEquals(java.util.List.of("openallay:crafting"), execution.modules());
    }
}
