package dev.openallay.context.minecraft;

import net.minecraft.world.EnumDifficulty;

/** Native difficulty display key, detached without translation. */
public final class MinecraftDifficultyFacts {
    private MinecraftDifficultyFacts() {}
    public static String name(EnumDifficulty difficulty) { return difficulty.getDifficultyResourceKey(); }
}
