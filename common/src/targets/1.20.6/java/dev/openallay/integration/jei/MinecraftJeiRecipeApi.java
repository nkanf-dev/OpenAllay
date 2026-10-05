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
 * JEI 16.0.0.28, 17.3.1.5 and 18.0.0.66 share this published native recipe-viewer ABI.
 * Newer JEI 15.62.0.219 backports the modern capabilities and restores the main binding.
 */
final class MinecraftJeiRecipeApi {
    private MinecraftJeiRecipeApi() {}

    static List<ITypedIngredient<?>> slotValues(IRecipeSlotView slot) {
        return slot.getAllIngredients().toList();
    }

    /** Neither IIngredientHelper nor IPlatformFluidHelper exposes a per-value quantity. */
    static <T> OptionalLong amount(IIngredientHelper<T> helper, T ingredient) {
        return OptionalLong.empty();
    }

    static boolean supportsExactRecipe() {
        return false;
    }

    /** Ingredient focuses and entire category views cannot select an exact recipe. */
    static <T> boolean showExact(IRecipesGui gui, IRecipeCategory<T> category, T recipe) {
        return false;
    }

    /**
     * This public layout contract consists of positioning, drawing and queries, with no
     * game-tick callback. Shared presentation ticks still validate the runtime generation.
     * Native drawRecipe/drawOverlays continue on each render; no missing callback is emulated.
     */
    static Optional<Runnable> layoutGameTick(IRecipeLayoutDrawable<?> layout) {
        Objects.requireNonNull(layout, "layout");
        return Optional.empty();
    }
}
