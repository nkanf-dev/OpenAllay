package dev.tomewisp.context;

import java.util.List;

public record RecipeSnapshot(List<RecipeEntrySnapshot> recipes) {
    public RecipeSnapshot {
        recipes = List.copyOf(recipes);
    }
}
