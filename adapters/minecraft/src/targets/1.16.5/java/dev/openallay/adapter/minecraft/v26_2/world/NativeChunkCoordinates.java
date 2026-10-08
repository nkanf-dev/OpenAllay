package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.util.math.ChunkPos;

/** Exact native packed chunk key. */
final class NativeChunkCoordinates {
    private NativeChunkCoordinates() {}
    static long pack(int x, int z) { return ChunkPos.asLong(x, z); }
}
