package dev.openallay.platform.minecraft;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Typed native registry identities; callers keep their existing capture and lookup behavior. */
public final class MinecraftNativeRegistries {
    private MinecraftNativeRegistries() {}
    public static final Registry<net.minecraft.world.entity.ai.attributes.Attribute> ATTRIBUTE = BuiltInRegistries.ATTRIBUTE;
    public static final Registry<net.minecraft.world.level.block.Block> BLOCK = BuiltInRegistries.BLOCK;
    public static final Registry<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPE = BuiltInRegistries.ENTITY_TYPE;
    public static final Registry<net.minecraft.world.level.material.Fluid> FLUID = BuiltInRegistries.FLUID;
    public static final Registry<net.minecraft.world.item.Item> ITEM = BuiltInRegistries.ITEM;
    public static final Registry<net.minecraft.world.inventory.MenuType<?>> MENU = BuiltInRegistries.MENU;
    public static final Registry<net.minecraft.world.effect.MobEffect> MOB_EFFECT = BuiltInRegistries.MOB_EFFECT;
    public static final Registry<net.minecraft.world.item.alchemy.Potion> POTION = BuiltInRegistries.POTION;
    public static final Registry<net.minecraft.world.item.crafting.RecipeType<?>> RECIPE_TYPE = BuiltInRegistries.RECIPE_TYPE;
}
