package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Minecraft 1.21.5 names the owning server-level accessor serverLevel(). */
final class NativeServerPlayerLevel {
    private NativeServerPlayerLevel() {}
    static ServerLevel get(ServerPlayer player) { return player.serverLevel(); }
}
