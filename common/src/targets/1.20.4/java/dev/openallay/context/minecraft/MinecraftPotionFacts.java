package dev.openallay.context.minecraft;

import net.minecraft.world.item.alchemy.Potion;

/** Pre-component potion naming, inherited by 1.20.3, 1.20.2 and 1.20.1. */
final class MinecraftPotionFacts {
    private MinecraftPotionFacts() {}
    static String name(Potion potion) { return potion.getName(""); }
}
