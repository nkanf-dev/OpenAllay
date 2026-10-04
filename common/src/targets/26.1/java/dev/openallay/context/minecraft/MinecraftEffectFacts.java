package dev.openallay.context.minecraft;

import net.minecraft.world.effect.MobEffect;

/** Native registry API naming stays outside detached catalog generation. */
final class MinecraftEffectFacts {
    private MinecraftEffectFacts() {}
    static boolean instantaneous(MobEffect effect) { return effect.isInstantenous(); }
}
