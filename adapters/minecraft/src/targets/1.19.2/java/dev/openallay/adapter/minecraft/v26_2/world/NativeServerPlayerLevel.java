package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Minecraft 1.19.2 names its exact owning server-level accessor getLevel(). */
final class NativeServerPlayerLevel {
    private NativeServerPlayerLevel() {}
    static ServerLevel get(ServerPlayer player) { return player.getLevel(); }
}
