package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Storage-only persistence admission for the already-validated target chunk. */
final class NativeChunkDirty {
    private NativeChunkDirty() {}
    static void mark(ServerLevel level, BlockPos pos) { level.getChunkAt(pos).markUnsaved(); }
}
