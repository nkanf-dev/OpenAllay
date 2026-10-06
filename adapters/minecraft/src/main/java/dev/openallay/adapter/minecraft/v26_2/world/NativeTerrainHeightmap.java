package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Native map readiness only; safe scan fallback stays in the shared session. */
final class NativeTerrainHeightmap {
    private NativeTerrainHeightmap() {}
    static boolean isPrimed(LevelChunk chunk, Heightmap.Types type) { return chunk.hasPrimedHeightmap(type); }
}
