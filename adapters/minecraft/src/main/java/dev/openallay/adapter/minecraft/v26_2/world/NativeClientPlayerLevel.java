package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

/** Native client-player level accessor only; exact session identity stays shared. */
final class NativeClientPlayerLevel {
    private NativeClientPlayerLevel() {}
    static Level get(LocalPlayer player) { return player.level(); }
}
