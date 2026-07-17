package dev.tomewisp.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tomewisp.context.DataCompleteness;
import dev.tomewisp.context.RecipeEntrySnapshot;
import dev.tomewisp.context.RecipeReference;
import dev.tomewisp.testing.GroundedTestFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RecipeProviderSnapshotTest {
    @Test
    void validatesGenerationAndProviderOwnership() {
        RecipeEntrySnapshot recipe = GroundedTestFixtures.ironBlockRecipe();
        RecipeProviderSnapshot snapshot = new RecipeProviderSnapshot(
                "minecraft:recipe_manager",
                GroundedTestFixtures.RECIPE_GENERATION,
                RecipeProviderState.AVAILABLE,
                DataCompleteness.COMPLETE,
                List.of(recipe),
                List.of());

        assertEquals(GroundedTestFixtures.RECIPE_GENERATION, snapshot.generation());
        assertThrows(IllegalArgumentException.class, () -> new RecipeProviderSnapshot(
                "viewer:jei",
                GroundedTestFixtures.RECIPE_GENERATION,
                RecipeProviderState.AVAILABLE,
                DataCompleteness.COMPLETE,
                List.of(recipe),
                List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RecipeReference(
                "viewer:jei", "ABC", "minecraft:iron_block"));
    }

    @Test
    void unavailableProvidersHaveNoGenerationOrRecords() {
        RecipeProviderSnapshot unavailable = new RecipeProviderSnapshot(
                "viewer:rei",
                null,
                RecipeProviderState.UNAVAILABLE,
                DataCompleteness.UNKNOWN,
                List.of(),
                List.of(new RecipeProviderDiagnostic(
                        "viewer:rei", "mod_not_loaded", "REI is not installed")));

        assertEquals(RecipeProviderState.UNAVAILABLE, unavailable.state());
        assertThrows(IllegalArgumentException.class, () -> new RecipeProviderSnapshot(
                "viewer:rei",
                null,
                RecipeProviderState.FAILED,
                DataCompleteness.PARTIAL,
                List.of(),
                List.of()));
    }
}
