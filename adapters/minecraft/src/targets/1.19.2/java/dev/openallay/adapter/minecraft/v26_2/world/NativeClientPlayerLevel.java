package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

/** Minecraft 1.19.2 exposes the client player's native level through getLevel(). */
final class NativeClientPlayerLevel {
    private NativeClientPlayerLevel() {}
    static Level get(LocalPlayer player) { return player.getLevel(); }
}
