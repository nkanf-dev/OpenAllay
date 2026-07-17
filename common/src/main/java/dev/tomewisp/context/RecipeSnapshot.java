package dev.tomewisp.context;

import java.util.List;
import dev.tomewisp.recipe.RecipeProviderSnapshot;

public record RecipeSnapshot(
        EvidenceMetadata evidence,
        List<RecipeEntrySnapshot> recipes,
        List<RecipeProviderSnapshot> providers) {
    public RecipeSnapshot {
        java.util.Objects.requireNonNull(evidence, "evidence");
        recipes = List.copyOf(recipes);
        providers = List.copyOf(providers);
    }

    public RecipeSnapshot(EvidenceMetadata evidence, List<RecipeEntrySnapshot> recipes) {
        this(evidence, recipes, List.of());
    }
}
