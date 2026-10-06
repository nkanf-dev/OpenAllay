package dev.openallay.context.minecraft;

import java.util.stream.Stream;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/** Actual native ingredient alternatives; recipe detachment remains shared. */
public final class MinecraftIngredientItems {
    private MinecraftIngredientItems() {}
    public static Stream<Item> items(Ingredient ingredient) { return ingredient.items().map(holder -> holder.value()); }
}
