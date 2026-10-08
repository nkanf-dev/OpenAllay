package dev.openallay.context.minecraft;

import net.minecraft.world.EnumDifficulty;

/** Native difficulty identity, detached without translation. */
public final class MinecraftDifficultyFacts {
    private MinecraftDifficultyFacts() {}
    public static String name(EnumDifficulty difficulty) { return difficulty.name().toLowerCase(java.util.Locale.ROOT); }
}
