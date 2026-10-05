package dev.openallay.context.minecraft;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Holder-era recipe facts, shared from 1.21.1 through 1.20.2. */
public final class MinecraftRecipeCapture {
    private MinecraftRecipeCapture() {}

    public static List<MinecraftRecipeInput> serverRecipes(CommandSourceStack source) {
        return source.getServer().getRecipeManager().getRecipes().stream()
                .map(holder -> input(holder, source.getLevel().registryAccess())).toList();
    }

    public static List<MinecraftRecipeInput> clientRecipes(LocalPlayer player, Minecraft client) {
        TreeMap<String, MinecraftRecipeInput> known = new TreeMap<>();
        player.getRecipeBook().getCollections().forEach(collection -> {
            for (RecipeHolder<?> holder : collection.getRecipes()) {
                if (player.getRecipeBook().contains(holder)) {
                    MinecraftRecipeInput input = input(holder, collection.registryAccess());
                    known.putIfAbsent(input.id(), input);
                }
            }
        });
        return List.copyOf(known.values());
    }

    public static int clientCollectionCount(LocalPlayer player) {
        return player.getRecipeBook().getCollections().size();
    }

    public static MinecraftRecipeSeed seed(ServerPlayer player, String id) {
        RecipeHolder<?> holder = player.serverLevel().getServer().getRecipeManager().getRecipes().stream()
                .filter(value -> id.equals(value.id().toString()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Exact native recipe is unavailable: " + id));
        List<MinecraftRecipeInput> inputs = List.of(input(holder, MinecraftServerPlayerLevel.get(player).registryAccess()));
        return new MinecraftRecipeSeed() {
            public String holderId() { return holder.id().toString(); }
            public String nativeRecipeClass() { return holder.value().getClass().getName(); }
            public List<MinecraftRecipeInput> inputs() { return inputs; }
            public boolean known() { return player.getRecipeBook().contains(holder); }
            public int award() { return player.awardRecipes(List.of(holder)); }
        };
    }

    private static MinecraftRecipeInput input(RecipeHolder<?> holder, RegistryAccess registries) {
        Recipe<?> recipe = holder.value();
        boolean shaped = recipe instanceof ShapedRecipe;
        int width = shaped ? ((ShapedRecipe) recipe).getWidth() : 0;
        int height = shaped ? ((ShapedRecipe) recipe).getHeight() : 0;
        ItemStack output = recipe.getResultItem(registries);
        // A toast icon is not an authoritative workstation contract. The old API has no display station.
        return new MinecraftRecipeInput(holder.id().toString(),
                Objects.requireNonNull(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())).toString(),
                recipe.getIngredients(), List.of(output), width, height, shaped,
                recipe instanceof CraftingRecipe, null);
    }
}
