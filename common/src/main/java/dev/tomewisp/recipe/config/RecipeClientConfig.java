package dev.tomewisp.recipe.config;

import dev.tomewisp.recipe.RecipeVisibilityPolicy;
import java.util.Objects;

public record RecipeClientConfig(
        int schemaVersion,
        RecipeVisibilityPolicy visibility,
        RecipeViewerPreference preferredViewer,
        boolean vanillaEnabled,
        boolean jeiEnabled,
        boolean reiEnabled) {
    public static final int SCHEMA_VERSION = 1;

    public RecipeClientConfig {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported recipe configuration schema");
        }
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(preferredViewer, "preferredViewer");
    }

    public static RecipeClientConfig defaults() {
        return new RecipeClientConfig(
                SCHEMA_VERSION,
                RecipeVisibilityPolicy.ALL_KNOWN,
                RecipeViewerPreference.AUTO,
                true,
                true,
                true);
    }
}
