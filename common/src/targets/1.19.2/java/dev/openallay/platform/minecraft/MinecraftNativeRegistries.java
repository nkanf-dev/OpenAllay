package dev.openallay.platform.minecraft;

import net.minecraft.core.Registry;

/** Typed native registry identities; callers keep their existing capture and lookup behavior. */
public final class MinecraftNativeRegistries {
    private MinecraftNativeRegistries() {}
    public static final Registry<net.minecraft.world.entity.ai.attributes.Attribute> ATTRIBUTE = Registry.ATTRIBUTE;
    public static final Registry<net.minecraft.world.level.block.Block> BLOCK = Registry.BLOCK;
    public static final Registry<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPE = Registry.ENTITY_TYPE;
    public static final Registry<net.minecraft.world.level.material.Fluid> FLUID = Registry.FLUID;
    public static final Registry<net.minecraft.world.item.Item> ITEM = Registry.ITEM;
    public static final Registry<net.minecraft.world.inventory.MenuType<?>> MENU = Registry.MENU;
    public static final Registry<net.minecraft.world.effect.MobEffect> MOB_EFFECT = Registry.MOB_EFFECT;
    public static final Registry<net.minecraft.world.item.alchemy.Potion> POTION = Registry.POTION;
    public static final Registry<net.minecraft.world.item.crafting.RecipeType<?>> RECIPE_TYPE = Registry.RECIPE_TYPE;
}
