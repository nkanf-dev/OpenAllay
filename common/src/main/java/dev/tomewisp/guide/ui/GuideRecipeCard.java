package dev.tomewisp.guide.ui;

import dev.tomewisp.context.RecipeReference;
import java.util.List;

/** Safe first-class projection for recipe results rendered by the native screen. */
public record GuideRecipeCard(
        RecipeReference reference,
        List<RecipeReference> references,
        String id,
        String type,
        String workstation,
        List<Output> outputs) {
    public GuideRecipeCard {
        java.util.Objects.requireNonNull(reference, "reference");
        references = List.copyOf(references);
        if (references.isEmpty() || !references.contains(reference)) {
            throw new IllegalArgumentException("recipe card references are incomplete");
        }
        if (id == null || id.isBlank() || type == null || type.isBlank()) {
            throw new IllegalArgumentException("recipe card identity is invalid");
        }
        workstation = workstation == null ? "" : workstation;
        outputs = List.copyOf(outputs);
    }

    public record Output(String itemId, int count, String displayName) {
        public Output {
            if (itemId == null || itemId.isBlank() || count <= 0) {
                throw new IllegalArgumentException("recipe card output is invalid");
            }
            displayName = displayName == null || displayName.isBlank() ? itemId : displayName;
        }
    }
}
