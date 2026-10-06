package dev.openallay.integration.jei;

import dev.openallay.platform.minecraft.MinecraftResourceId;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.ingredients.IIngredientHelper;
import net.minecraft.world.item.crafting.Recipe;

/** Real recipe IDs only; non-native recipes retain the shared semantic-fingerprint fallback. */
final class MinecraftJeiResourceIds {
    private MinecraftJeiResourceIds() {}
    static <T> MinecraftResourceId recipe(IRecipeCategory<T> category, T recipe) {
        return recipe instanceof Recipe<?> nativeRecipe ? MinecraftResourceId.from(nativeRecipe.getId().toString()) : null;
    }
    static <V> MinecraftResourceId ingredient(IIngredientHelper<V> helper, V ingredient) {
        return MinecraftResourceId.from(helper.getModId(ingredient) + ":" + helper.getResourceId(ingredient));
    }
}
