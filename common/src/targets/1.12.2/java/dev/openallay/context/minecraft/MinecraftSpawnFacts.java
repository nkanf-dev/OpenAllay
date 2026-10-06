package dev.openallay.context.minecraft;

import net.minecraft.world.World;

/** Native dimension integer and spawn BlockPos, using approved dimension spelling. */
public final class MinecraftSpawnFacts {
    private MinecraftSpawnFacts() {}
    public static String describe(World level) {
        var position = level.getSpawnPoint();
        return "forge:dimension/" + level.provider.getDimension() + " "
                + position.getX() + ", " + position.getY() + ", " + position.getZ();
    }
}
