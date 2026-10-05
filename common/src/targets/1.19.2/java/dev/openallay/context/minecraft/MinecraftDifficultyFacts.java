package dev.openallay.context.minecraft;

import net.minecraft.world.Difficulty;

/** Native difficulty name used by public world-query facts. */
public final class MinecraftDifficultyFacts {
    private MinecraftDifficultyFacts() {}
    public static String name(Difficulty difficulty) { return difficulty.getKey(); }
}
