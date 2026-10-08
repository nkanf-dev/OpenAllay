package dev.openallay.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

/** Exact old Entity owns its live public level field. */
public final class MinecraftLocalPlayerLevel {
    private MinecraftLocalPlayerLevel() {}
    public static Level get(LocalPlayer player) { return player.level; }
}
