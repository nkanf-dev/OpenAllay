package dev.openallay.context.minecraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** 1.21.1/1.21 expose the owning server level through serverLevel(). */
public final class MinecraftServerPlayerLevel {
    private MinecraftServerPlayerLevel() {}
    public static ServerLevel get(ServerPlayer player) { return player.serverLevel(); }
}
