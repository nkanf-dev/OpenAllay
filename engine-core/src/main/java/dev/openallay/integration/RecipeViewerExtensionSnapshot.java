package dev.openallay.integration;

import dev.openallay.context.DataCompleteness;
import dev.openallay.recipe.RecipeProviderState;
import java.util.List;

/**
 * Detached recipe-viewer capability projected into the request-scoped Extension root.
 *
 * <p>The projection contains stable OpenAllay references only. It deliberately cannot retain a
 * JEI runtime, REI registry, recipe widget, focus object, or other client-thread-owned value.
 */
public record RecipeViewerExtensionSnapshot(
        String provider,
        boolean available,
        RecipeProviderState state,
        DataCompleteness completeness,
        String generation,
        int recipeCount,
        List<String> categories,
        List<Recipe> recipes,
        boolean focusSupported,
        boolean navigationSupported,
        boolean exactNavigationSupported,
        List<Diagnostic> diagnostics) {
    public RecipeViewerExtensionSnapshot {
        provider = require(provider, "provider");
        java.util.Objects.requireNonNull(state, "state");
        java.util.Objects.requireNonNull(completeness, "completeness");
        if (available != (state == RecipeProviderState.AVAILABLE)) {
            throw new IllegalArgumentException("availability must match provider state");
        }
        if (available && (generation == null || generation.isBlank())) {
            throw new IllegalArgumentException("available provider requires a generation");
        }
        if (!available && generation != null) {
            throw new IllegalArgumentException("unavailable provider cannot expose a generation");
        }
        if (recipeCount < 0) {
            throw new IllegalArgumentException("recipeCount must be non-negative");
        }
        categories = List.copyOf(categories);
        recipes = List.copyOf(recipes);
        diagnostics = List.copyOf(diagnostics);
        if (recipeCount != recipes.size()) {
            throw new IllegalArgumentException("recipeCount must match recipes");
        }
    }

    public record Recipe(
            String sourceId,
            String generation,
            String referenceId,
            String recipeId,
            String category) {
        public Recipe {
            sourceId = require(sourceId, "sourceId");
            generation = require(generation, "generation");
            referenceId = require(referenceId, "referenceId");
            recipeId = require(recipeId, "recipeId");
            category = require(category, "category");
        }
    }

    public record Diagnostic(String code, String message) {
        public Diagnostic {
            code = require(code, "code");
            message = require(message, "message");
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
