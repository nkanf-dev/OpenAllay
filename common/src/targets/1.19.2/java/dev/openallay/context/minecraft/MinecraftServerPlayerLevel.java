package dev.openallay.context.minecraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** 1.19.2 expose the owning server level through getLevel(). */
public final class MinecraftServerPlayerLevel {
    private MinecraftServerPlayerLevel() {}
    public static ServerLevel get(ServerPlayer player) { return player.getLevel(); }
}
