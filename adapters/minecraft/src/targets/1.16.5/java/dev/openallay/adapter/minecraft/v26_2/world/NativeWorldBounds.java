package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/** Native horizontal/build-height limits. Border/loaded-owner checks stay shared. */
final class NativeWorldBounds {
    private NativeWorldBounds() {}
    static boolean contains(ServerWorld level, BlockPos pos) { return World.isInWorldBounds(pos); }
}
