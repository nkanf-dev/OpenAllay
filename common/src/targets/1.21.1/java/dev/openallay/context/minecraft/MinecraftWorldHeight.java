package dev.openallay.context.minecraft;

import net.minecraft.world.level.LevelHeightAccessor;

/** Earlier native vertical bounds names, inherited from 1.21.1 through 1.20.1. */
public final class MinecraftWorldHeight {
    private MinecraftWorldHeight() {}
    public static int min(LevelHeightAccessor level) { return level.getMinBuildHeight(); }
    public static int max(LevelHeightAccessor level) { return level.getMaxBuildHeight(); }
}
