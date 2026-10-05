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

/** Native recipe-viewer ABI with explicit selection, quantity and layout lifecycle capabilities. */
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
