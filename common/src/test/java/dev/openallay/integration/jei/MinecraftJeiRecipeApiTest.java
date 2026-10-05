package dev.openallay.integration.jei;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IRecipesGui;
import org.junit.jupiter.api.Test;

/** The same recipe business body consumes each publication's real native capabilities. */
final class MinecraftJeiRecipeApiTest {
    @Test
    void slotValuesRetainEveryNativeAlternative() {
        ITypedIngredient<String> first = proxy(ITypedIngredient.class, (self, method, args) -> null);
        ITypedIngredient<String> second = proxy(ITypedIngredient.class, (self, method, args) -> null);
        List<ITypedIngredient<?>> values = List.of(first, second);
        AtomicReference<String> operation = new AtomicReference<>();
        IRecipeSlotView slot = proxy(IRecipeSlotView.class, (self, method, args) -> {
            operation.set(method.getName());
            return switch (method.getName()) {
                case "getAllIngredientsList" -> values;
                case "getAllIngredients" -> values.stream();
                default -> throw new AssertionError("Unexpected slot operation: " + method.getName());
            };
        });
        List<ITypedIngredient<?>> captured = MinecraftJeiRecipeApi.slotValues(slot);
        assertEquals(values.size(), captured.size());
        assertSame(first, captured.get(0));
        assertSame(second, captured.get(1));
        assertEquals(MinecraftJeiRecipeApi.supportsExactRecipe()
                ? "getAllIngredientsList" : "getAllIngredients", operation.get());
    }

    @Test
    void amountUsesTypedHelperOrReportsNoNativeQuantityReader() {
        AtomicInteger reads = new AtomicInteger();
        IIngredientHelper<String> helper = proxy(IIngredientHelper.class, (self, method, args) -> {
            assertEquals("getAmount", method.getName());
            reads.incrementAndGet();
            return args[0].equals("known") ? 2500L : -1L;
        });
        OptionalLong known = MinecraftJeiRecipeApi.amount(helper, "known");
        OptionalLong unknown = MinecraftJeiRecipeApi.amount(helper, "unknown");
        if (MinecraftJeiRecipeApi.supportsExactRecipe()) {
            assertEquals(2500L, known.orElseThrow());
            assertEquals(-1L, unknown.orElseThrow());
            assertEquals(2, reads.get());
        } else {
            assertTrue(known.isEmpty());
            assertTrue(unknown.isEmpty());
            assertEquals(0, reads.get());
        }
    }

    @Test
    void exactSelectionNeverSubstitutesAnIngredientOrCategoryView() {
        IRecipeCategory<String> category = proxy(IRecipeCategory.class, (self, method, args) -> null);
        AtomicInteger opens = new AtomicInteger();
        IRecipesGui gui = proxy(IRecipesGui.class, (self, method, args) -> {
            assertEquals("showRecipes", method.getName());
            assertSame(category, args[0]);
            assertEquals(List.of("requested"), args[1]);
            assertEquals(List.of(), args[2]);
            opens.incrementAndGet();
            return null;
        });
        boolean opened = MinecraftJeiRecipeApi.showExact(gui, category, "requested");
        assertEquals(MinecraftJeiRecipeApi.supportsExactRecipe(), opened);
        assertEquals(opened ? 1 : 0, opens.get());
    }

    @Test
    void nativeLayoutTickIsARealCallbackOrExplicitlyAbsent() {
        AtomicInteger ticks = new AtomicInteger();
        IRecipeLayoutDrawable<String> layout = proxy(IRecipeLayoutDrawable.class, (self, method, args) -> {
            assertEquals("tick", method.getName());
            ticks.incrementAndGet();
            return null;
        });
        Optional<Runnable> callback = MinecraftJeiRecipeApi.layoutGameTick(layout);
        assertEquals(MinecraftJeiRecipeApi.supportsExactRecipe(), callback.isPresent());
        assertEquals(0, ticks.get());
        callback.ifPresent(Runnable::run);
        assertEquals(callback.isPresent() ? 1 : 0, ticks.get());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
