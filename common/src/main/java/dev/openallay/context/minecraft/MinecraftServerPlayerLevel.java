package dev.openallay.context.minecraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Native owning server-level accessor for captures that require ServerLevel. */
public final class MinecraftServerPlayerLevel {
    private MinecraftServerPlayerLevel() {}
    public static ServerLevel get(ServerPlayer player) { return player.level(); }
}
