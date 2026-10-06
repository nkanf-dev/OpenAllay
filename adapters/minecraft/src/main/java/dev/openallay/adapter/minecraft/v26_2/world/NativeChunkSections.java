package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Native section access and conservative palette proof. */
final class NativeChunkSections {
    private NativeChunkSections() {}
    static boolean supportsCanonicalAir(ServerLevel level) { return true; }
    static LevelChunkSection get(LevelChunk chunk, int y) { return chunk.getSection(chunk.getSectionIndex(y)); }
    static boolean canonicalAir(LevelChunkSection section) {
        return !section.maybeHas(state -> state != Blocks.AIR.defaultBlockState());
    }
}
