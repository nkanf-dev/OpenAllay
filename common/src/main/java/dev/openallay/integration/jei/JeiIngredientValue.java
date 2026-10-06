package dev.openallay.integration.jei;

import java.util.Objects;
import java.util.OptionalLong;
import net.minecraft.world.item.ItemStack;

/** Detached publication projection. Native item custody remains inside optional JEI code. */
sealed interface JeiIngredientValue {
    record Item(ItemStack stack) implements JeiIngredientValue {
        public Item { Objects.requireNonNull(stack, "stack"); }
    }
    record Fluid(String id, OptionalLong amount) implements JeiIngredientValue {
        public Fluid { Objects.requireNonNull(id, "id"); Objects.requireNonNull(amount, "amount"); }
    }
    record Unsupported(String ingredientType) implements JeiIngredientValue {}
}
