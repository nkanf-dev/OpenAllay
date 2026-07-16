package dev.tomewisp.context;

import java.util.List;

public record IngredientSlotSnapshot(List<String> alternatives) {
    public IngredientSlotSnapshot {
        alternatives = List.copyOf(alternatives);
        alternatives.forEach(value -> ContextValidation.identifier(value, "alternative"));
    }
}
