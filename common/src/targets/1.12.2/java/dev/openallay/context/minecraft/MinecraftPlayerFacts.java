package dev.openallay.context.minecraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.world.GameType;

/** Real1.12 inventory/selection/game-mode facts. */
public final class MinecraftPlayerFacts {
    private MinecraftPlayerFacts() {}
    public static InventoryPlayer inventory(EntityPlayer player) { return player.inventory; }
    public static int selectedSlot(InventoryPlayer inventory) { return inventory.currentItem; }
    public static GameType gameMode(EntityPlayerMP player) { return player.interactionManager.getGameType(); }
}
