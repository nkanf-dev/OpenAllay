package dev.openallay.context.minecraft;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import dev.openallay.platform.minecraft.MinecraftResourceIds;

/** Typed native snapshot values, with no context-selection or detachment policy. */
public final class MinecraftServerCaptureFacts {
    private MinecraftServerCaptureFacts() {}
    public static double x(ServerPlayer player) { return player.getX(); }
    public static double y(ServerPlayer player) { return player.getY(); }
    public static double z(ServerPlayer player) { return player.getZ(); }
    public static BlockPos position(ServerPlayer player) { return player.blockPosition(); }
    public static String name(ServerPlayer player) { return player.getName().getString(); }
    public static String dimension(ServerPlayer player) {
        return MinecraftResourceIds.keyId(MinecraftServerPlayerLevel.get(player).dimension()).toString();
    }
    public static ItemStack offhand(ServerPlayer player) { return player.getOffhandItem(); }
    public static int inventorySize(Inventory inventory) { return inventory.getContainerSize(); }
    public static ItemStack inventoryStack(Inventory inventory, int slot) { return inventory.getItem(slot); }
    public static String itemName(ItemStack stack) { return stack.getHoverName().getString(); }
    public static String uiState(ServerPlayer player) {
        return player.containerMenu == player.inventoryMenu ? "gameplay_or_player_inventory" : "open_synchronized_menu";
    }
    public static String menuId(ServerPlayer player) {
        return player.containerMenu == player.inventoryMenu || player.containerMenu.getType() == null ? ""
                : MinecraftNativeRegistries.MENU.getKey(player.containerMenu.getType()).toString();
    }
    public static long gameTime(Level level) { return level.getGameTime(); }
    public static double borderSize(Level level) { return level.getWorldBorder().getSize(); }
}
