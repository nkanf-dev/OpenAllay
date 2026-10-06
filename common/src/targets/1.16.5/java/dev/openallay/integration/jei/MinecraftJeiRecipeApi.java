package dev.openallay.integration.jei;

import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.platform.minecraft.MinecraftResourceId;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

/** Exact published JEI 7.8.1.1018 API; no runtime-helper or exact-viewer access. */
final class MinecraftJeiRecipeApi {
    private MinecraftJeiRecipeApi() {}

    static List<IRecipeCategory<?>> categories(IJeiRuntime runtime, boolean includeHidden) {
        return runtime.getRecipeManager().getRecipeCategories(null, includeHidden);
    }
    static <T> List<T> recipes(IJeiRuntime runtime, IRecipeCategory<T> category, boolean includeHidden) {
        return runtime.getRecipeManager().getRecipes(category, null, includeHidden);
    }
    static MinecraftResourceId categoryId(IRecipeCategory<?> category) {
        return MinecraftResourceId.from(category.getUid().toString());
    }
    static Component categoryTitle(IRecipeCategory<?> category) { return category.getTitleAsTextComponent(); }
    static boolean supportsExactRecipe() { return false; }
    static <T> boolean showExact(IRecipesGui gui, IRecipeCategory<T> category, T recipe) { return false; }
    static void showItem(IJeiRuntime runtime, ItemStack stack, JeiIngredientSlot.Role role) {
        runtime.getRecipesGui().show(runtime.getRecipeManager().createFocus(
                role == JeiIngredientSlot.Role.INPUT ? IFocus.Mode.INPUT : IFocus.Mode.OUTPUT, stack));
    }
    static <T> Optional<NativeRecipeLayout<T>> createLayout(IJeiRuntime runtime, IRecipeCategory<T> category, T recipe) {
        return Optional.ofNullable(runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe, null))
                .map(handle -> new Layout<T>(handle, category));
    }

    private static List<JeiIngredientSlot> captureSlots(
            IJeiRuntime runtime, IRecipeLayoutDrawable layout, IRecipeCategory<?> category) {
        List<JeiIngredientSlot> result = new ArrayList<>();
        runtime.getIngredientManager().getRegisteredIngredientTypes().stream()
                .sorted(Comparator.comparing(type -> type.getIngredientClass().getName()))
                .forEach(type -> captureGroup(runtime, layout, type, result));
        // JEI exposes all workstation choices as one category-level list, not consumed inputs.
        List<JeiIngredientValue> catalysts = new ArrayList<>();
        for (Object value : runtime.getRecipeManager().getRecipeCatalysts(category, true)) {
            if (value != null) catalysts.add(captureCatalyst(runtime, value));
        }
        if (!catalysts.isEmpty()) result.add(new JeiIngredientSlot(JeiIngredientSlot.Role.WORKSTATION, catalysts));
        return List.copyOf(result);
    }

    private static <V> void captureGroup(IJeiRuntime runtime, IRecipeLayoutDrawable layout,
            IIngredientType<V> type, List<JeiIngredientSlot> result) {
        var group = layout.getIngredientsGroup(type);
        if (group == null) return;
        group.getGuiIngredients().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            var slot = entry.getValue();
            if (slot == null) return;
            List<JeiIngredientValue> values = slot.getAllIngredients().stream().filter(Objects::nonNull)
                    .map(value -> detach(type, value)).toList();
            result.add(new JeiIngredientSlot(slot.isInput() ? JeiIngredientSlot.Role.INPUT : JeiIngredientSlot.Role.OUTPUT, values));
        });
    }

    // Object is the publication's catalyst list ABI only. Real registered type owns the cast.
    private static JeiIngredientValue captureCatalyst(IJeiRuntime runtime, Object value) {
        try { return castCatalyst(runtime.getIngredientManager().getIngredientType(value), value); }
        catch (IllegalArgumentException unregistered) {
            return new JeiIngredientValue.Unsupported(value.getClass().getName());
        }
    }
    private static <V> JeiIngredientValue castCatalyst(IIngredientType<V> type, Object value) {
        if (type == null || !type.getIngredientClass().isInstance(value)) {
            return new JeiIngredientValue.Unsupported(value.getClass().getName());
        }
        return detach(type, type.getIngredientClass().cast(value));
    }
    private static <V> JeiIngredientValue detach(IIngredientType<V> type, V value) {
        if (type.equals(VanillaTypes.ITEM)) {
            return new JeiIngredientValue.Item(VanillaTypes.ITEM.getIngredientClass().cast(value));
        }
        if (type.equals(VanillaTypes.FLUID)) {
            FluidStack fluid = VanillaTypes.FLUID.getIngredientClass().cast(value);
            return new JeiIngredientValue.Fluid(MinecraftNativeRegistries.FLUID.getKey(fluid.getFluid()).toString(),
                    OptionalLong.of(fluid.getAmount()));
        }
        return new JeiIngredientValue.Unsupported(type.getIngredientClass().getName());
    }

    private record Layout<T>(IRecipeLayoutDrawable handle, IRecipeCategory<T> category) implements NativeRecipeLayout<T> {
        private Layout { Objects.requireNonNull(handle, "handle"); Objects.requireNonNull(category, "category"); }
        @Override public int width() { return category.getBackground().getWidth(); }
        @Override public int height() { return category.getBackground().getHeight(); }
        @Override public List<JeiIngredientSlot> captureSlots(IJeiRuntime runtime) {
            return MinecraftJeiRecipeApi.captureSlots(runtime, handle, category);
        }
        @Override public void setPosition(int x, int y) { handle.setPosition(x, y); }
        @Override public void drawRecipe(GuideGraphics graphics, int mouseX, int mouseY) {
            handle.drawRecipe(graphics.nativeGraphics(), mouseX, mouseY);
        }
        @Override public void drawOverlays(GuideGraphics graphics, int mouseX, int mouseY) {
            handle.drawOverlays(graphics.nativeGraphics(), mouseX, mouseY);
        }
        @Override public Optional<Runnable> gameTick() { return Optional.empty(); }
    }
}
