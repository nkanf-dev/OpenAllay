package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.world.level.ChunkPos;

/** Native chunk-key naming only; session behavior does not depend on the native family. */
final class NativeChunkCoordinates {
    private NativeChunkCoordinates() {}
    static long pack(int x, int z) { return ChunkPos.asLong(x, z); }
}
