package dev.openallay.context.minecraft;

import java.util.Arrays;
import java.util.stream.Stream;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/** 1.21.1/1.21 ingredient alternatives are ItemStack[], not a holder collection. */
public final class MinecraftIngredientItems {
    private MinecraftIngredientItems() {}
    public static Stream<Item> items(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty()).map(stack -> stack.getItem());
    }
}
