package dev.openallay.client.context;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.ItemStack;
/** Direct legacy native screen/menu facts, without modern GUI/type aliases. */
public final class MinecraftFocusNativeFacts {
    private MinecraftFocusNativeFacts() {}
    public static String availability() { return "native_screen_title_not_available;native_menu_type_registry_not_available"; }
    public static Object screen(Minecraft client) { return client.currentScreen; }
    public static Object overlay(Minecraft client) { return null; }
    public static boolean container(Object screen) { return screen instanceof GuiContainer; }
    public static int screenWidth(Minecraft client,Object screen) { return screen==null ? new ScaledResolution(client).getScaledWidth() : ((GuiScreen)screen).width; }
    public static int screenHeight(Minecraft client,Object screen) { return screen==null ? new ScaledResolution(client).getScaledHeight() : ((GuiScreen)screen).height; }
    public static String screenTitle(Object screen) { return ""; }
    public static boolean pauses(Object screen) { return ((GuiScreen)screen).doesGuiPauseGame(); }
    public static boolean inGame(Minecraft client,Object screen) { return client.world!=null; }
    public static Container menu(Minecraft client,Object screen) { return screen instanceof GuiContainer container ? container.inventorySlots : client.player.openContainer; }
    public static String menuType(Container menu) { throw new UnsupportedOperationException("Legacy container has no MenuType registry identity"); }
    public static int menuId(Container menu) { return menu.windowId; }
    public static int slotCount(Container menu) { return menu.inventorySlots.size(); }
    public static boolean active(Minecraft client,Container menu) { return menu==client.player.openContainer; }
    public static int slotIndex(Slot slot) { return slot.slotNumber; }
    public static String itemName(ItemStack stack) { return stack.getDisplayName(); }
    public static int damage(ItemStack stack) { return stack.getItemDamage(); }
    public static String entityName(Entity entity) { return entity.getDisplayName().getUnformattedText(); }
    public static Vec3d entityPosition(Entity entity) { return entity.getPositionVector(); }
    public static double x(Vec3d position) { return position.x; }
    public static double y(Vec3d position) { return position.y; }
    public static double z(Vec3d position) { return position.z; }
}
