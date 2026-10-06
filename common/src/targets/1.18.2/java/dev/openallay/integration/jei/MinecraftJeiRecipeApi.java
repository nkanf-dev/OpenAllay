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

/** JEI 10.81.0.1029 native ABI: a non-generic drawable with typed factory inputs. */
final class MinecraftJeiRecipeApi {
    private MinecraftJeiRecipeApi() {}

    static List<ITypedIngredient<?>> slotValues(IRecipeSlotView slot) {
        return slot.getAllIngredientsList().stream().filter(Objects::nonNull).toList();
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
    static Optional<Runnable> layoutGameTick(IRecipeLayoutDrawable layout) {
        Objects.requireNonNull(layout, "layout");
        return Optional.of(layout::tick);
    }

    static <T> Optional<NativeRecipeLayout<T>> createLayout(
            IRecipeManager manager, IRecipeCategory<T> category, T recipe, IFocusGroup focuses) {
        return manager.createRecipeLayoutDrawable(category, recipe, focuses)
                .map(handle -> new Layout<>(handle, category, recipe));
    }

    private record Layout<T>(
            IRecipeLayoutDrawable handle, IRecipeCategory<T> category, T recipe) implements NativeRecipeLayout<T> {
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
        @Override public Rect2i getRectWithBorder() { return handle.getRectWithBorder(); }
        @Override public Optional<SlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
            return handle.getSlotUnderMouse(mouseX, mouseY)
                    .map(slot -> new SlotUnderMouse(slot.slot(), slot.x(), slot.y()));
        }
        @Override public <I> Optional<I> getIngredientUnderMouse(
                int mouseX, int mouseY, IIngredientType<I> type) {
            return handle.getOptionalIngredientUnderMouse(mouseX, mouseY, type);
        }
        @Override public Optional<Runnable> gameTick() { return layoutGameTick(handle); }
    }
}
