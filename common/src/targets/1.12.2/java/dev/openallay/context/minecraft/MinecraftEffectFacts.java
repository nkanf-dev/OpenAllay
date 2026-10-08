package dev.openallay.context.minecraft;

import net.minecraft.potion.Potion;

/** Actual Forge 14 potion-effect instantaneous flag. */
final class MinecraftEffectFacts {
    private MinecraftEffectFacts() {}
    static boolean instantaneous(Potion effect) { return effect.isInstant(); }
}
