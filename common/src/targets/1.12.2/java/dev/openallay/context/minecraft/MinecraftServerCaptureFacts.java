package dev.openallay.context.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Actual MCP1.12 native snapshot values; no modern menu registry or dimension alias. */
public final class MinecraftServerCaptureFacts {
    private MinecraftServerCaptureFacts() {}
    public static double x(EntityPlayerMP player) { return player.posX; }
    public static double y(EntityPlayerMP player) { return player.posY; }
    public static double z(EntityPlayerMP player) { return player.posZ; }
    public static BlockPos position(EntityPlayerMP player) { return player.getPosition(); }
    public static String name(EntityPlayerMP player) { return player.getName(); }
    public static String dimension(EntityPlayerMP player) {
        return "forge:dimension/" + player.getServerWorld().provider.getDimension();
    }
    public static ItemStack offhand(EntityPlayerMP player) { return player.getHeldItemOffhand(); }
    public static int inventorySize(InventoryPlayer inventory) { return inventory.getSizeInventory(); }
    public static ItemStack inventoryStack(InventoryPlayer inventory, int slot) { return inventory.getStackInSlot(slot); }
    public static String itemName(ItemStack stack) { return stack.getDisplayName(); }
    public static String uiState(EntityPlayerMP player) {
        return player.openContainer == player.inventoryContainer ? "gameplay_or_player_inventory" : "open_synchronized_menu";
    }
    public static String menuId(EntityPlayerMP player) {
        // Native Container1.12 has no registered menu-type identity.
        return "";
    }
    public static long gameTime(World level) { return level.getTotalWorldTime(); }
    public static double borderSize(World level) { return level.getWorldBorder().getDiameter(); }
}
