package dev.openallay.integration.jei;

import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.ingredients.IIngredientHelper;
import dev.openallay.platform.minecraft.MinecraftResourceId;

/** Optional viewer ID names are detached at the native API boundary. */
final class MinecraftJeiResourceIds {
    private MinecraftJeiResourceIds() {}
    static <T> MinecraftResourceId recipe(IRecipeCategory<T> category, T recipe) {
        var id = category.getRegistryName(recipe);
        return id == null ? null : MinecraftResourceId.from(id.toString());
    }
    static <T> MinecraftResourceId ingredient(IIngredientHelper<T> helper, T ingredient) {
        var id = helper.getResourceLocation(ingredient);
        return id == null ? null : MinecraftResourceId.from(id.toString());
    }
}
