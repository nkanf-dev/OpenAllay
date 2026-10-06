package dev.openallay.context.minecraft;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Reads the spawn coordinates from the native level's saved data. */
public final class MinecraftSpawnFacts {
    private MinecraftSpawnFacts() {}
    public static String describe(Level level) {
        var data = level.getLevelData();
        return MinecraftResourceIds.keyId(level.dimension()) + " "
                + new BlockPos(data.getXSpawn(), data.getYSpawn(), data.getZSpawn()).toShortString();
    }
}
