package dev.openallay.integration.rei;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import dev.openallay.client.gui.GuideNativeItemLookup;

import dev.openallay.context.RecipeReference;
import dev.openallay.recipe.RecipeNavigationResult;
import dev.openallay.recipe.RecipeViewerNavigator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import net.minecraft.world.item.ItemStack;

final class ReiRecipeNavigator implements RecipeViewerNavigator {
    @Override
    public String viewerId() {
        return "viewer:rei";
    }

    @Override
    public boolean supportsExactRecipe() {
        return false;
    }

    @Override
    public RecipeNavigationResult openRecipes(String itemId) {
        return openItem(itemId, true);
    }

    @Override
    public RecipeNavigationResult openUsages(String itemId) {
        return openItem(itemId, false);
    }

    @Override
    public RecipeNavigationResult openExact(RecipeReference reference) {
        return RecipeNavigationResult.failed(
                "exact_unsupported", "REI does not expose an exact display selector");
    }

    private RecipeNavigationResult openItem(String itemId, boolean recipes) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            return RecipeNavigationResult.failed(
                    "wrong_thread", "Recipe viewer navigation requires the client thread");
        }
        var id = MinecraftResourceIds.tryParse(itemId);
        if (id == null || !MinecraftNativeRegistries.ITEM.containsKey(id)) {
            return RecipeNavigationResult.failed("unknown_item", "Item is not registered");
        }
        try {
            var entry = EntryStacks.of(new ItemStack(GuideNativeItemLookup.item(itemId)));
            ViewSearchBuilder builder = ViewSearchBuilder.builder();
            boolean opened = recipes
                    ? builder.addRecipesFor(entry).open()
                    : builder.addUsagesFor(entry).open();
            return opened
                    ? RecipeNavigationResult.success()
                    : RecipeNavigationResult.failed(
                            "viewer_unavailable", "REI did not open a view");
        } catch (RuntimeException failure) {
            return RecipeNavigationResult.failed(
                    "viewer_failure", "REI could not open the requested item view");
        }
    }
}
