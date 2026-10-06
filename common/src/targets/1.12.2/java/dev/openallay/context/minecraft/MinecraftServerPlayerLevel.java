package dev.openallay.context.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

/** Actual original player's native owning world. */
public final class MinecraftServerPlayerLevel {
    private MinecraftServerPlayerLevel() {}
    public static WorldServer get(EntityPlayerMP player) { return player.getServerWorld(); }
}
