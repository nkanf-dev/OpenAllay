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
import mezz.jei.api.runtime.IJeiRuntime;

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

    static <T> Optional<NativeRecipeLayout<T>> createLayout(
            IRecipeManager manager, IRecipeCategory<T> category, T recipe, IFocusGroup focuses) {
        return manager.createRecipeLayoutDrawable(category, recipe, focuses)
                .map(handle -> new Layout<>(handle, category));
    }

    static <T> Optional<NativeRecipeLayout<T>> createLayout(
            IJeiRuntime runtime, IRecipeCategory<T> category, T recipe) {
        return createLayout(runtime.getRecipeManager(), category, recipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
    }


    static List<IRecipeCategory<?>> categories(IJeiRuntime runtime, boolean includeHidden) {
        var lookup = runtime.getRecipeManager().createRecipeCategoryLookup();
        if (includeHidden) lookup.includeHidden();
        return lookup.get().toList();
    }

    static <T> List<T> recipes(IJeiRuntime runtime, IRecipeCategory<T> category, boolean includeHidden) {
        var lookup = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType());
        if (includeHidden) lookup.includeHidden();
        return lookup.get().toList();
    }

    static dev.openallay.platform.minecraft.MinecraftResourceId categoryId(IRecipeCategory<?> category) {
        return dev.openallay.platform.minecraft.MinecraftResourceId.from(category.getRecipeType().getUid().toString());
    }

    static net.minecraft.network.chat.Component categoryTitle(IRecipeCategory<?> category) {
        return category.getTitle();
    }

    static void showItem(IJeiRuntime runtime, net.minecraft.world.item.ItemStack stack, JeiIngredientSlot.Role role) {
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(
                role == JeiIngredientSlot.Role.INPUT
                        ? mezz.jei.api.recipe.RecipeIngredientRole.INPUT : mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
    }

    private static List<JeiIngredientSlot> captureSlots(IJeiRuntime runtime, IRecipeSlotsView slots) {
        return slots.getSlotViews().stream().map(slot -> {
            var role = role(slot.getRole());
            return new JeiIngredientSlot(role, role == JeiIngredientSlot.Role.RENDER_ONLY ? List.of()
                    : slotValues(slot).stream().filter(Objects::nonNull).map(value -> detach(runtime, value)).toList());
        }).toList();
    }

    private static JeiIngredientSlot.Role role(mezz.jei.api.recipe.RecipeIngredientRole role) {
        if (role == mezz.jei.api.recipe.RecipeIngredientRole.INPUT) return JeiIngredientSlot.Role.INPUT;
        if (role == mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT) return JeiIngredientSlot.Role.OUTPUT;
        if (role == mezz.jei.api.recipe.RecipeIngredientRole.RENDER_ONLY) return JeiIngredientSlot.Role.RENDER_ONLY;
        if (MinecraftJeiIngredientRoles.craftingStation(role)) return JeiIngredientSlot.Role.WORKSTATION;
        throw new IllegalArgumentException("Unsupported JEI ingredient role: " + role);
    }

    private static <V> JeiIngredientValue detach(IJeiRuntime runtime, ITypedIngredient<V> value) {
        var item = value.getItemStack();
        if (item.isPresent()) return new JeiIngredientValue.Item(item.get());
        IIngredientType<V> type = value.getType();
        if (type.equals(runtime.getJeiHelpers().getPlatformFluidHelper().getFluidIngredientType())) {
            IIngredientHelper<V> helper = runtime.getIngredientManager().getIngredientHelper(type);
            var id = MinecraftJeiResourceIds.ingredient(helper, value.getIngredient());
            return new JeiIngredientValue.Fluid(id.toString(), amount(helper, value.getIngredient()));
        }
        return new JeiIngredientValue.Unsupported(type.getIngredientClass().getName());
    }

    private record Layout<T>(
            IRecipeLayoutDrawable<T> handle, IRecipeCategory<T> category) implements NativeRecipeLayout<T> {
        private Layout {
            Objects.requireNonNull(handle, "handle");
            Objects.requireNonNull(category, "category");
        }

        @Override public int width() { return category.getWidth(); }
        @Override public int height() { return category.getHeight(); }
        @Override public List<JeiIngredientSlot> captureSlots(IJeiRuntime runtime) {
            return MinecraftJeiRecipeApi.captureSlots(runtime, handle.getRecipeSlotsView());
        }
        @Override public void setPosition(int x, int y) { handle.setPosition(x, y); }
        @Override public void drawRecipe(GuideGraphics graphics, int mouseX, int mouseY) {
            handle.drawRecipe(graphics.nativeGraphics(), mouseX, mouseY);
        }
        @Override public void drawOverlays(GuideGraphics graphics, int mouseX, int mouseY) {
            handle.drawOverlays(graphics.nativeGraphics(), mouseX, mouseY);
        }
        @Override public Optional<Runnable> gameTick() { return layoutGameTick(handle); }
    }
}
