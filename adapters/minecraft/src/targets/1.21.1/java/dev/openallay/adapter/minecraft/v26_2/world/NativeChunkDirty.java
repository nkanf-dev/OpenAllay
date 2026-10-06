package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Pre-1.21.3 native storage flag; no comparator or block-entity attachment hook. */
final class NativeChunkDirty {
    private NativeChunkDirty() {}
    static void mark(ServerLevel level, BlockPos pos) { level.getChunkAt(pos).setUnsaved(true); }
}
