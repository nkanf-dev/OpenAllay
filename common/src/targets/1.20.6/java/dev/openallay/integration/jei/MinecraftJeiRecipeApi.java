package dev.openallay.integration.jei;

import dev.openallay.client.gui.GuideGraphics;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.renderer.Rect2i;

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

    static <T> Optional<NativeRecipeLayout<T>> createLayout(
            IRecipeManager manager, IRecipeCategory<T> category, T recipe, IFocusGroup focuses) {
        return manager.createRecipeLayoutDrawable(category, recipe, focuses)
                .map(handle -> new Layout<>(handle, category, recipe));
    }

    private record Layout<T>(
            IRecipeLayoutDrawable<T> handle, IRecipeCategory<T> category, T recipe) implements NativeRecipeLayout<T> {
        private Layout {
            Objects.requireNonNull(handle, "handle");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(recipe, "recipe");
        }

        @Override public IRecipeCategory<T> getRecipeCategory() { return category; }
        @Override public T getRecipe() { return recipe; }
        @Override public IRecipeSlotsView getRecipeSlotsView() { return handle.getRecipeSlotsView(); }
        @Override public void setPosition(int x, int y) { handle.setPosition(x, y); }
        @Override public void drawRecipe(GuideGraphics graphics, int mouseX, int mouseY) {
            handle.drawRecipe(graphics.nativeGraphics(), mouseX, mouseY);
        }
        @Override public void drawOverlays(GuideGraphics graphics, int mouseX, int mouseY) {
            handle.drawOverlays(graphics.nativeGraphics(), mouseX, mouseY);
        }
        @Override public boolean isMouseOver(double mouseX, double mouseY) {
            return handle.isMouseOver(mouseX, mouseY);
        }
        @Override public Rect2i getRect() { return handle.getRect(); }
        @Override public Rect2i getRectWithBorder() { return handle.getRect(); }
        @Override public Optional<SlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
            return handle.getRecipeSlotUnderMouse(mouseX, mouseY)
                    .map(slot -> new SlotUnderMouse(slot, 0, 0));
        }
        @Override public <I> Optional<I> getIngredientUnderMouse(
                int mouseX, int mouseY, IIngredientType<I> type) {
            return handle.getIngredientUnderMouse(mouseX, mouseY, type);
        }
        @Override public Optional<Runnable> gameTick() { return layoutGameTick(handle); }
    }
}
