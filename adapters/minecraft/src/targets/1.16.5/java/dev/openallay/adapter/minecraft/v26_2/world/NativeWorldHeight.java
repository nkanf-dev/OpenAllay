package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.world.server.ServerWorld;

/** Actual World.isOutsideBuildHeight geometry, half-open [0,256). */
final class NativeWorldHeight {
    private NativeWorldHeight() {}
    static int minY(ServerWorld level) { return 0; }
    static int maxYExclusive(ServerWorld level) { return 256; }
}
