package dev.openallay.context.minecraft;

import net.minecraft.potion.PotionType;

/** Exact native potion translation suffix; never derive it from the registry path. */
final class MinecraftPotionFacts {
    private MinecraftPotionFacts() {}
    static String name(PotionType potion) { return potion.getNamePrefixed(""); }
}
