package dev.tomewisp.recipe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tomewisp.context.RecipeReference;
import dev.tomewisp.recipe.RecipeNavigationResult;
import dev.tomewisp.recipe.RecipeViewerNavigator;
import dev.tomewisp.recipe.RecipeVisibilityPolicy;
import dev.tomewisp.tool.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RecipeClientRuntimeTest {
    @TempDir Path directory;

    @Test
    void loadsPersistedConfigAndRetainsLastValidStateOnReloadFailure() throws Exception {
        Path path = directory.resolve("recipes.json");
        Files.writeString(path, """
                {"schemaVersion":1,"visibility":"unlocked_only","preferredViewer":"rei",
                "sources":{"vanilla":false,"jei":false,"rei":true}}
                """);
        RecipeClientRuntime runtime = new RecipeClientRuntime(path);

        assertEquals(RecipeVisibilityPolicy.UNLOCKED_ONLY, runtime.config().visibility());
        assertFalse(runtime.sourceEnabled("minecraft:client_recipe_book"));
        assertFalse(runtime.sourceEnabled("viewer:jei"));
        assertTrue(runtime.sourceEnabled("viewer:rei"));

        Files.writeString(path, "{}");
        assertInstanceOf(ToolResult.Failure.class, runtime.reload());
        assertEquals(RecipeViewerPreference.REI, runtime.config().preferredViewer());
        assertTrue(runtime.failure().isPresent());
    }

    @Test
    void preferenceAndExactOwnershipSelectOnlyEnabledViewer() {
        AtomicInteger jeiRecipes = new AtomicInteger();
        AtomicInteger reiRecipes = new AtomicInteger();
        RecipeViewerNavigator jei = navigator("viewer:jei", true, jeiRecipes);
        RecipeViewerNavigator rei = navigator("viewer:rei", false, reiRecipes);
        RecipeClientConfig config = new RecipeClientConfig(
                RecipeClientConfig.SCHEMA_VERSION,
                RecipeVisibilityPolicy.ALL_KNOWN,
                RecipeViewerPreference.REI,
                true,
                true,
                true);
        RecipeClientRuntime runtime = RecipeClientRuntime.forTest(config, () -> List.of(jei, rei));

        assertTrue(runtime.openRecipes("minecraft:iron_ingot").opened());
        assertEquals(0, jeiRecipes.get());
        assertEquals(1, reiRecipes.get());
        assertFalse(runtime.supportsExact(reference("viewer:rei")));
        assertTrue(runtime.supportsExact(reference("viewer:jei")));
        assertEquals("exact_unsupported", runtime.openExact(reference("viewer:rei")).code());
        assertTrue(runtime.openExact(reference("viewer:jei")).opened());
    }

    private static RecipeReference reference(String sourceId) {
        return new RecipeReference(sourceId, "0".repeat(64), "test:recipe");
    }

    private static RecipeViewerNavigator navigator(
            String id, boolean exact, AtomicInteger recipes) {
        return new RecipeViewerNavigator() {
            @Override public String viewerId() { return id; }
            @Override public boolean supportsExactRecipe() { return exact; }
            @Override public RecipeNavigationResult openRecipes(String itemId) {
                recipes.incrementAndGet();
                return RecipeNavigationResult.success();
            }
            @Override public RecipeNavigationResult openUsages(String itemId) {
                return RecipeNavigationResult.success();
            }
            @Override public RecipeNavigationResult openExact(RecipeReference reference) {
                return RecipeNavigationResult.success();
            }
        };
    }
}
