package dev.openallay.context.minecraft;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Forge 14 crafting registry facts. Shared MinecraftRecipeInput detachment owns recipe policy. */
public final class MinecraftRecipeCapture {
    private MinecraftRecipeCapture() {}
    public static List<MinecraftRecipeInput> serverRecipes(ICommandSender source) {
        Objects.requireNonNull(source, "source");
        return ForgeRegistries.RECIPES.getValuesCollection().stream()
                .map(MinecraftRecipeCapture::input).toList();
    }
    public static List<MinecraftRecipeInput> clientRecipes(EntityPlayerSP player, Minecraft client) {
        return ForgeRegistries.RECIPES.getValuesCollection().stream()
                .filter(recipe -> player.getRecipeBook().isUnlocked(recipe))
                .map(MinecraftRecipeCapture::input).toList();
    }
    public static int clientCollectionCount(EntityPlayerSP player) {
        // Native RecipeBook 1.12 stores recipe membership, not modern display collections.
        return clientRecipes(player, null).size();
    }
    public static MinecraftRecipeSeed seed(EntityPlayerMP player, String id) {
        IRecipe recipe = ForgeRegistries.RECIPES.getValue(new ResourceLocation(id));
        if (recipe == null || !id.equals(recipe.getRegistryName().toString())) {
            throw new IllegalStateException("Exact native recipe is unavailable: " + id);
        }
        List<MinecraftRecipeInput> inputs = List.of(input(recipe));
        return new MinecraftRecipeSeed() {
            public String holderId() { return recipe.getRegistryName().toString(); }
            public String nativeRecipeClass() { return recipe.getClass().getName(); }
            public List<MinecraftRecipeInput> inputs() { return inputs; }
            public boolean known() { return player.getRecipeBook().isUnlocked(recipe); }
            public int award() {
                boolean wasKnown = known();
                player.unlockRecipes(List.of(recipe));
                return !wasKnown && known() ? 1 : 0;
            }
        };
    }
    private static MinecraftRecipeInput input(IRecipe recipe) {
        boolean shaped = recipe instanceof IShapedRecipe;
        int width = shaped ? ((IShapedRecipe) recipe).getRecipeWidth() : 0;
        int height = shaped ? ((IShapedRecipe) recipe).getRecipeHeight() : 0;
        // IRecipe is the native crafting contract; no RecipeType registry exists in 1.12.
        return new MinecraftRecipeInput(Objects.requireNonNull(recipe.getRegistryName()).toString(),
                "minecraft:crafting", recipe.getIngredients(), List.of(recipe.getRecipeOutput()),
                width, height, shaped, true, null);
    }
}
