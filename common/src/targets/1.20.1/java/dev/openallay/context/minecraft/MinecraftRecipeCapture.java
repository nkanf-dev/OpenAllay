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
import net.minecraft.world.item.crafting.ShapedRecipe;

/** 1.20.1 native recipes carry their own IDs; RecipeHolder does not exist. */
public final class MinecraftRecipeCapture {
    private MinecraftRecipeCapture() {}
    public static List<MinecraftRecipeInput> serverRecipes(CommandSourceStack source) {
        return source.getServer().getRecipeManager().getRecipes().stream()
                .map(recipe -> input(recipe, source.getLevel().registryAccess())).toList();
    }
    public static List<MinecraftRecipeInput> clientRecipes(LocalPlayer player, Minecraft client) {
        TreeMap<String, MinecraftRecipeInput> known = new TreeMap<>();
        player.getRecipeBook().getCollections().forEach(collection -> {
            for (Recipe<?> recipe : collection.getRecipes()) {
                if (player.getRecipeBook().contains(recipe)) {
                    MinecraftRecipeInput input = input(recipe, collection.registryAccess());
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
        Recipe<?> recipe = player.serverLevel().getServer().getRecipeManager().getRecipes().stream()
                .filter(value -> id.equals(value.getId().toString()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Exact native recipe is unavailable: " + id));
        List<MinecraftRecipeInput> inputs = List.of(input(recipe, MinecraftServerPlayerLevel.get(player).registryAccess()));
        return new MinecraftRecipeSeed() {
            public String holderId() { return recipe.getId().toString(); }
            public String nativeRecipeClass() { return recipe.getClass().getName(); }
            public List<MinecraftRecipeInput> inputs() { return inputs; }
            public boolean known() { return player.getRecipeBook().contains(recipe); }
            public int award() { return player.awardRecipes(List.of(recipe)); }
        };
    }
    private static MinecraftRecipeInput input(Recipe<?> recipe, RegistryAccess registries) {
        boolean shaped = recipe instanceof ShapedRecipe;
        int width = shaped ? ((ShapedRecipe) recipe).getWidth() : 0;
        int height = shaped ? ((ShapedRecipe) recipe).getHeight() : 0;
        ItemStack output = recipe.getResultItem(registries);
        return new MinecraftRecipeInput(recipe.getId().toString(),
                Objects.requireNonNull(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())).toString(),
                recipe.getIngredients(), List.of(output), width, height, shaped,
                recipe instanceof CraftingRecipe, null);
    }
}
