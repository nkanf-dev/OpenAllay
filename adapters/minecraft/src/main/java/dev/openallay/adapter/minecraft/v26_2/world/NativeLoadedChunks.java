package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Actual native loaded-chunk check; no chunk handle survives an owner action. */
final class NativeLoadedChunks {
    private NativeLoadedChunks() {}
    static void require(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos))
            throw new ExtensionException("chunk_unavailable", "Chunk is not loaded; no implicit chunk generation: " + pos);
    }
}
