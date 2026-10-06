package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.server.ServerWorld;

/** Exact 16-section storage; debug worlds do not use their section palette for every block. */
final class NativeChunkSections {
    private NativeChunkSections() {}
    static boolean supportsCanonicalAir(ServerWorld level) { return !level.isDebug(); }
    static ChunkSection get(Chunk chunk, int y) { return chunk.getSections()[Math.floorDiv(y, 16)]; }
    static boolean canonicalAir(ChunkSection section) {
        return section == Chunk.EMPTY_SECTION
                || !section.maybeHas(state -> state != Blocks.AIR.defaultBlockState());
    }
}
