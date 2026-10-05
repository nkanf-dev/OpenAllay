package dev.openallay.context.minecraft;

import net.minecraft.world.item.alchemy.Potion;

/** Native potion name only; catalog translation and detached data remain shared. */
final class MinecraftPotionFacts {
    private MinecraftPotionFacts() {}
    static String name(Potion potion) { return potion.name(); }
}
