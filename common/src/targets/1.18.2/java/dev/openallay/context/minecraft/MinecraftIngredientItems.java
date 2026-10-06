package dev.openallay.context.minecraft;

import java.util.Arrays;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/** Resolves native stack alternatives to their registered item holders. */
public final class MinecraftIngredientItems {
    private MinecraftIngredientItems() {}
    public static Stream<Holder<Item>> items(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                .map(stack -> Registry.ITEM.getHolderOrThrow(
                        Registry.ITEM.getResourceKey(stack.getItem()).orElseThrow()));
    }
}
