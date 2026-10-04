package dev.openallay.context.minecraft;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.GameType;

/** Native player facts only; detached context and capture behavior stay shared. */
public final class MinecraftPlayerFacts {
    private MinecraftPlayerFacts() {}
    public static int selectedSlot(Inventory inventory) { return inventory.selected; }
    public static GameType gameMode(ServerPlayer player) { return player.gameMode.getGameModeForPlayer(); }
}
