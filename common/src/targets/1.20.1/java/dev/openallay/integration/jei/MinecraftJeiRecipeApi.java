package dev.openallay.integration.jei;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IRecipesGui;

/**
 * JEI 15.62.0.219 backports modern recipe-viewer capabilities to Minecraft 1.20.1.
 * Restore the main ABI binding instead of inheriting the older 16/17/18 publication shape.
 */
final class MinecraftJeiRecipeApi {
    private MinecraftJeiRecipeApi() {}

    static List<ITypedIngredient<?>> slotValues(IRecipeSlotView slot) {
        return slot.getAllIngredientsList();
    }

    static <T> OptionalLong amount(IIngredientHelper<T> helper, T ingredient) {
        return OptionalLong.of(helper.getAmount(ingredient));
    }

    static boolean supportsExactRecipe() {
        return true;
    }

    static <T> boolean showExact(IRecipesGui gui, IRecipeCategory<T> category, T recipe) {
        gui.showRecipes(category, List.of(recipe), List.of());
        return true;
    }

    /** A native layout tick is required by publications that expose this callback. */
    static Optional<Runnable> layoutGameTick(IRecipeLayoutDrawable<?> layout) {
        Objects.requireNonNull(layout, "layout");
        return Optional.of(layout::tick);
    }
}
