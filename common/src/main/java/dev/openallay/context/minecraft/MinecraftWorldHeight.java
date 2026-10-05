package dev.openallay.context.minecraft;

import net.minecraft.world.level.LevelHeightAccessor;

/** Native vertical bounds only; owning-thread spatial capture remains shared. */
public final class MinecraftWorldHeight {
    private MinecraftWorldHeight() {}
    public static int min(LevelHeightAccessor level) { return level.getMinY(); }
    public static int max(LevelHeightAccessor level) { return level.getMaxY(); }
}
