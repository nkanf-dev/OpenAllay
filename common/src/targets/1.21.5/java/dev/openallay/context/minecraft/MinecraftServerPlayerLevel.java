package dev.openallay.context.minecraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Minecraft 1.21.5 names the owning server-level accessor serverLevel(). */
public final class MinecraftServerPlayerLevel {
    private MinecraftServerPlayerLevel() {}
    public static ServerLevel get(ServerPlayer player) { return player.serverLevel(); }
}
