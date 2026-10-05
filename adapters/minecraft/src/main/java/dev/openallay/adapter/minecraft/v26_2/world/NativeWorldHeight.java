package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.server.level.ServerLevel;

/** Native vertical bounds expressed as the shared half-open [minY, maxY) range. */
final class NativeWorldHeight {
    private NativeWorldHeight() {}
    static int minY(ServerLevel level) { return level.getMinY(); }
    static int maxYExclusive(ServerLevel level) { return Math.addExact(level.getMaxY(), 1); }
}
