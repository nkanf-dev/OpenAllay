package dev.tomewisp.recipe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tomewisp.recipe.RecipeVisibilityPolicy;
import dev.tomewisp.tool.ToolResult;
import java.io.StringReader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RecipeClientConfigLoaderTest {
    private final RecipeClientConfigLoader loader = new RecipeClientConfigLoader();

    @Test
    void missingFileUsesAllKnownDefaults(@TempDir Path directory) {
        RecipeClientConfig config = success(loader.load(directory.resolve("missing.json"))).value();

        assertEquals(RecipeVisibilityPolicy.ALL_KNOWN, config.visibility());
        assertEquals(RecipeViewerPreference.AUTO, config.preferredViewer());
        assertTrue(config.vanillaEnabled());
        assertTrue(config.jeiEnabled());
        assertTrue(config.reiEnabled());
    }

    @Test
    void parsesExactSchemaVersionOne() {
        RecipeClientConfig config = success(loader.load(new StringReader("""
                {
                  "schemaVersion": 1,
                  "visibility": "unlocked_only",
                  "preferredViewer": "jei",
                  "sources": {"vanilla": true, "jei": false, "rei": true}
                }
                """))).value();

        assertEquals(RecipeVisibilityPolicy.UNLOCKED_ONLY, config.visibility());
        assertEquals(RecipeViewerPreference.JEI, config.preferredViewer());
        assertTrue(!config.jeiEnabled());
    }

    @Test
    void rejectsMissingUnknownAndNonIntegralFields() {
        assertInvalid("""
                {"schemaVersion":1,"visibility":"all_known","preferredViewer":"auto"}
                """);
        assertInvalid("""
                {"schemaVersion":1,"visibility":"all_known","preferredViewer":"auto",
                 "sources":{"vanilla":true,"jei":true,"rei":true},"extra":true}
                """);
        assertInvalid("""
                {"schemaVersion":1.5,"visibility":"all_known","preferredViewer":"auto",
                 "sources":{"vanilla":true,"jei":true,"rei":true}}
                """);
    }

    private void assertInvalid(String json) {
        ToolResult.Failure<RecipeClientConfig> failure = assertInstanceOf(
                ToolResult.Failure.class, loader.load(new StringReader(json)));
        assertEquals("invalid_recipe_config", failure.code());
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Success<RecipeClientConfig> success(
            ToolResult<RecipeClientConfig> result) {
        return (ToolResult.Success<RecipeClientConfig>)
                assertInstanceOf(ToolResult.Success.class, result);
    }
}
