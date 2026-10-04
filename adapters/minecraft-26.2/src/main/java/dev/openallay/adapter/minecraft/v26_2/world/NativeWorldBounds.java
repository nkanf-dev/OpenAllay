package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Native world/build-height bounds, keeping the caller's complete owner-thread checks. */
final class NativeWorldBounds {
    private NativeWorldBounds() {}
    static boolean contains(ServerLevel level, BlockPos pos) { return level.isInValidBounds(pos); }
}
