package dev.openallay.platform.minecraft;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionType;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

/** Actual Forge 14 registries. Unregistered attributes, menus and recipe types are not invented. */
public final class MinecraftNativeRegistries {
    private MinecraftNativeRegistries() {}
    public static final IForgeRegistry<Block> BLOCK = ForgeRegistries.BLOCKS;
    public static final IForgeRegistry<Item> ITEM = ForgeRegistries.ITEMS;
    public static final IForgeRegistry<Potion> MOB_EFFECT = ForgeRegistries.POTIONS;
    public static final IForgeRegistry<PotionType> POTION = ForgeRegistries.POTION_TYPES;
    public static final IForgeRegistry<EntityEntry> ENTITY_TYPE = ForgeRegistries.ENTITIES;
    public static final IForgeRegistry<IRecipe> RECIPE = ForgeRegistries.RECIPES;
    public static java.util.Collection<net.minecraft.util.ResourceLocation> blockKeys() { return BLOCK.getKeys(); }
}
