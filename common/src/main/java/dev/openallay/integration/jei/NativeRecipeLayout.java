package dev.openallay.integration.jei;

import dev.openallay.client.gui.GuideGraphics;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.renderer.Rect2i;

/** Product-owned typed layout port; publication-specific handles stay in the native factory. */
interface NativeRecipeLayout<T> {
    IRecipeCategory<T> getRecipeCategory();

    T getRecipe();

    IRecipeSlotsView getRecipeSlotsView();

    void setPosition(int x, int y);

    void drawRecipe(GuideGraphics graphics, int mouseX, int mouseY);

    /** Delegate native ingredient and category tooltips, including recipe-specific callbacks. */
    void drawOverlays(GuideGraphics graphics, int mouseX, int mouseY);

    boolean isMouseOver(double mouseX, double mouseY);

    Rect2i getRect();

    Rect2i getRectWithBorder();

    Optional<SlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY);

    <I> Optional<I> getIngredientUnderMouse(int mouseX, int mouseY, IIngredientType<I> type);

    Optional<Runnable> gameTick();

    /** Preserve native slot identity and parent offsets without a publication-specific class. */
    record SlotUnderMouse(IRecipeSlotView slot, int x, int y) {}
}
