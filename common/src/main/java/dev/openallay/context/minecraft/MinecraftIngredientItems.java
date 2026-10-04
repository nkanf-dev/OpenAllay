package dev.openallay.context.minecraft;

import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/** Actual native ingredient alternatives; recipe detachment remains shared. */
public final class MinecraftIngredientItems {
    private MinecraftIngredientItems() {}
    public static Stream<Holder<Item>> items(Ingredient ingredient) { return ingredient.items(); }
}
