package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Native owning server-level accessor only; session capture and identity remain shared. */
final class NativeServerPlayerLevel {
    private NativeServerPlayerLevel() {}
    static ServerLevel get(ServerPlayer player) { return player.level(); }
}
