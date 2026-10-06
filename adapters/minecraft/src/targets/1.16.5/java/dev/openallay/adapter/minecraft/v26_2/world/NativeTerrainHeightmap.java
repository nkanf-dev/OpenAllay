package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.Heightmap;

/** 1.16.5 creates map entries before priming and exposes no primed proof. */
final class NativeTerrainHeightmap {
    private NativeTerrainHeightmap() {}
    static boolean isPrimed(Chunk chunk, Heightmap.Type type) {
        // The shared session uses its defined maxY-1 safe scan start. Map presence
        // cannot prove readiness: the native constructor allocates zero-filled maps.
        return false;
    }
}
