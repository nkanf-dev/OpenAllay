package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Native world/build-height bounds; owner-thread and loaded-chunk checks stay shared. */
final class NativeWorldBounds {
    private NativeWorldBounds() {}
    static boolean contains(ServerLevel level, BlockPos pos) { return level.isInWorldBounds(pos); }
}
