package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.world.WorldServer;
import net.minecraft.util.math.BlockPos;
final class NativeWorldBounds {
    private NativeWorldBounds() {}
    static boolean contains(WorldServer level,BlockPos pos) { return level.isValid(pos); }
}
