package dev.tomewisp.recipe;

import dev.tomewisp.context.DataCompleteness;
import dev.tomewisp.context.RecipeEntrySnapshot;
import dev.tomewisp.context.RecipeReference;
import java.util.List;
import java.util.Objects;

public record RecipeProviderSnapshot(
        String sourceId,
        String generation,
        RecipeProviderState state,
        DataCompleteness completeness,
        List<RecipeEntrySnapshot> recipes,
        List<RecipeProviderDiagnostic> diagnostics) {
    public RecipeProviderSnapshot {
        sourceId = RecipeReference.requireSourceId(sourceId);
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(completeness, "completeness");
        recipes = List.copyOf(recipes);
        diagnostics = List.copyOf(diagnostics);
        if (state == RecipeProviderState.AVAILABLE) {
            RecipeReference.requireGeneration(generation);
        } else if (generation != null) {
            throw new IllegalArgumentException("unavailable provider must not expose a generation");
        }
        if (state != RecipeProviderState.AVAILABLE && !recipes.isEmpty()) {
            throw new IllegalArgumentException("unavailable provider must not expose recipes");
        }
        if (state != RecipeProviderState.AVAILABLE && completeness != DataCompleteness.UNKNOWN) {
            throw new IllegalArgumentException("unavailable provider completeness must be unknown");
        }
        for (RecipeEntrySnapshot recipe : recipes) {
            if (!recipe.reference().sourceId().equals(sourceId)
                    || !recipe.reference().generation().equals(generation)) {
                throw new IllegalArgumentException("recipe belongs to another provider generation");
            }
        }
        for (RecipeProviderDiagnostic diagnostic : diagnostics) {
            if (!diagnostic.sourceId().equals(sourceId)) {
                throw new IllegalArgumentException("diagnostic belongs to another provider");
            }
        }
    }
}
