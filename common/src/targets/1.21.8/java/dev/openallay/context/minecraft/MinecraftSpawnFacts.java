package dev.openallay.context.minecraft;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.world.level.Level;

/** Detach the current world's actual native spawn facts. */
public final class MinecraftSpawnFacts {
    private MinecraftSpawnFacts() {}
    public static String describe(Level level) { return MinecraftResourceIds.keyId(level.dimension()) + " " + level.getLevelData().getSpawnPos().toShortString(); }
}
