package dev.openallay.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

/** Returns the world owned by the actor whose context is being captured. */
public final class MinecraftLocalPlayerLevel {
    private MinecraftLocalPlayerLevel() {}
    public static Level get(LocalPlayer player) { return player.getLevel(); }
}
