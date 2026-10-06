package dev.openallay.context.minecraft;

import java.util.Arrays;
import java.util.stream.Stream;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.Ingredient;

/** Exact Forge 14 ingredient alternatives. Shared recipe detachment remains the only algorithm. */
public final class MinecraftIngredientItems {
    private MinecraftIngredientItems() {}
    public static Stream<Item> items(Ingredient ingredient) {
        return Arrays.stream(ingredient.getMatchingStacks()).filter(stack -> !stack.isEmpty())
                .map(stack -> stack.getItem());
    }
}
