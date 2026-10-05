package dev.openallay.context.minecraft;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Owner-thread native recipe facts. Shared code detaches these into immutable engine snapshots. */
public record MinecraftRecipeInput(
        String id, String type, List<Ingredient> ingredients, List<ItemStack> outputs,
        int width, int height, boolean shaped, boolean crafting, String workstation) {
    public MinecraftRecipeInput {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        ingredients = List.copyOf(ingredients);
        outputs = outputs.stream().map(ItemStack::copy).toList();
    }
}
