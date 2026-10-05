package dev.openallay.context.minecraft;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.world.level.Level;

/** Pre-component Level exposes the native shared spawn position directly. */
public final class MinecraftSpawnFacts {
    private MinecraftSpawnFacts() {}
    public static String describe(Level level) {
        return MinecraftResourceIds.keyId(level.dimension()) + " " + level.getSharedSpawnPos().toShortString();
    }
}
