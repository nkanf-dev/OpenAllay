package dev.tomewisp.context;

import java.util.List;

public record RecipeEntrySnapshot(
        String id,
        String type,
        List<IngredientSlotSnapshot> ingredients,
        List<ItemStackSnapshot> outputs,
        String provenance) {
    public RecipeEntrySnapshot {
        id = ContextValidation.identifier(id, "id");
        type = ContextValidation.identifier(type, "type");
        ingredients = List.copyOf(ingredients);
        outputs = List.copyOf(outputs);
        provenance = ContextValidation.identifier(provenance, "provenance");
    }
}
