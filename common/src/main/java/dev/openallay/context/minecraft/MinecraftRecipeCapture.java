package dev.openallay.context.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/** Typed display-family recipe facts only. Detachment and provider policy remain shared. */
public final class MinecraftRecipeCapture {
    private MinecraftRecipeCapture() {}

    public static List<MinecraftRecipeInput> serverRecipes(CommandSourceStack source) {
        ContextMap context = SlotDisplayContext.fromLevel(source.getLevel());
        return source.getServer().getRecipeManager().getRecipes().stream()
                .map(holder -> serverInput(holder, context)).toList();
    }

    public static List<MinecraftRecipeInput> clientRecipes(LocalPlayer player, Minecraft client) {
        ContextMap context = SlotDisplayContext.fromLevel(client.level);
        return player.getRecipeBook().getCollections().stream()
                .flatMap(collection -> collection.getRecipes().stream())
                .map(entry -> displayInput(entry, context)).toList();
    }

    public static int clientCollectionCount(LocalPlayer player) {
        return player.getRecipeBook().getCollections().size();
    }

    public static MinecraftRecipeSeed seed(ServerPlayer player, String id) {
        var manager = MinecraftServerPlayerLevel.get(player).getServer().getRecipeManager();
        RecipeHolder<?> holder = manager.getRecipes().stream()
                .filter(value -> id.equals(dev.openallay.platform.minecraft.MinecraftResourceIds.keyId(value.id())))
                .findFirst().orElseThrow(() -> new IllegalStateException("Exact native recipe is unavailable: " + id));
        List<RecipeDisplayEntry> displays = new ArrayList<>();
        manager.listDisplaysForRecipe(holder.id(), displays::add);
        ContextMap context = SlotDisplayContext.fromLevel(player.level());
        List<MinecraftRecipeInput> inputs = displays.stream()
                .map(entry -> displayInput(entry, context)).toList();
        return new MinecraftRecipeSeed() {
            public String holderId() { return dev.openallay.platform.minecraft.MinecraftResourceIds.keyId(holder.id()); }
            public String nativeRecipeClass() { return holder.value().getClass().getName(); }
            public List<MinecraftRecipeInput> inputs() { return inputs; }
            public boolean known() { return player.getRecipeBook().contains(holder.id()); }
            public int award() { return player.awardRecipes(List.of(holder)); }
        };
    }

    private static MinecraftRecipeInput serverInput(RecipeHolder<?> holder, ContextMap context) {
        List<RecipeDisplay> displays = holder.value().display();
        RecipeDisplay primary = displays.isEmpty() ? null : displays.get(0);
        return input(dev.openallay.platform.minecraft.MinecraftResourceIds.keyId(holder.id()),
                Objects.requireNonNull(dev.openallay.platform.minecraft.MinecraftNativeRegistries.RECIPE_TYPE.getKey(holder.value().getType())).toString(),
                holder.value().placementInfo().ingredients(),
                displays.stream().flatMap(display -> display.result().resolveForStacks(context).stream()).toList(),
                primary, context);
    }

    private static MinecraftRecipeInput displayInput(RecipeDisplayEntry entry, ContextMap context) {
        return input("openallay:client_recipe_display/" + entry.id().index(),
                Objects.requireNonNull(BuiltInRegistries.RECIPE_DISPLAY.getKey(entry.display().type())).toString(),
                entry.craftingRequirements().orElse(List.of()), entry.resultItems(context), entry.display(), context);
    }

    private static MinecraftRecipeInput input(String id, String type,
            List<net.minecraft.world.item.crafting.Ingredient> ingredients, List<ItemStack> outputs,
            RecipeDisplay display, ContextMap context) {
        boolean shaped = display instanceof ShapedCraftingRecipeDisplay;
        int width = shaped ? ((ShapedCraftingRecipeDisplay) display).width() : 0;
        int height = shaped ? ((ShapedCraftingRecipeDisplay) display).height() : 0;
        String workstation = display == null ? null : display.craftingStation().resolveForStacks(context).stream()
                .filter(value -> !value.isEmpty()).map(value -> dev.openallay.platform.minecraft.MinecraftNativeRegistries.ITEM.getKey(value.getItem()).toString())
                .findFirst().orElse(null);
        return new MinecraftRecipeInput(id, type, ingredients, outputs, width, height, shaped,
                shaped || display instanceof ShapelessCraftingRecipeDisplay, workstation);
    }
}
