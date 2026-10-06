package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerWorld;

/** Public 1.16.5 owner-only nonblocking FULL chunk access. */
final class NativeLoadedChunks {
    private NativeLoadedChunks() {}
    static Chunk get(ServerWorld level, BlockPos pos) {
        if (!level.getServer().isSameThread())
            throw new ExtensionException("wrong_owner", "Loaded chunk access requires the server owner thread");
        Chunk chunk = level.getChunkSource().getChunkNow(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16));
        if (chunk == null)
            throw new ExtensionException("chunk_unavailable", "Chunk is not loaded; no implicit chunk generation: " + pos);
        return chunk;
    }
    static void require(ServerWorld level, BlockPos pos) { get(level, pos); }
}
