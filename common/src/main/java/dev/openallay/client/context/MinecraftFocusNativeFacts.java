package dev.openallay.client.context;
import dev.openallay.client.gui.MinecraftClientWindow;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
public final class MinecraftFocusNativeFacts {
    private MinecraftFocusNativeFacts() {}
    public static ItemStack slotItem(Slot slot) { return slot.getItem(); }
    public static String availability() { return ""; }
    public static Object screen(Minecraft client) { return MinecraftClientWindow.screen(client); }
    public static Object overlay(Minecraft client) { return MinecraftClientWindow.overlay(client); }
    public static boolean container(Object screen) { return screen instanceof AbstractContainerScreen<?>; }
    public static int screenWidth(Minecraft client,Object screen) { return screen==null ? client.getWindow().getGuiScaledWidth() : ((Screen)screen).width; }
    public static int screenHeight(Minecraft client,Object screen) { return screen==null ? client.getWindow().getGuiScaledHeight() : ((Screen)screen).height; }
    public static String screenTitle(Object screen) { return ((Screen)screen).getTitle().getString(); }
    public static boolean pauses(Object screen) { return ((Screen)screen).isPauseScreen(); }
    public static boolean inGame(Minecraft client,Object screen) { return MinecraftClientWindow.isInGameUi(client,(Screen)screen); }
    public static AbstractContainerMenu menu(Minecraft client,Object screen) { return screen instanceof AbstractContainerScreen<?> container ? container.getMenu() : client.player.containerMenu; }
    public static String menuType(AbstractContainerMenu menu) { return MinecraftNativeRegistries.MENU.getKey(menu.getType()).toString(); }
    public static int menuId(AbstractContainerMenu menu) { return menu.containerId; }
    public static int slotCount(AbstractContainerMenu menu) { return menu.slots.size(); }
    public static boolean active(Minecraft client,AbstractContainerMenu menu) { return menu==client.player.containerMenu; }
    public static int slotIndex(Slot slot) { return slot.index; }
    public static String itemName(ItemStack stack) { return stack.getHoverName().getString(); }
    public static int damage(ItemStack stack) { return stack.getDamageValue(); }
    public static String entityName(Entity entity) { return entity.getName().getString(); }
    public static Vec3 entityPosition(Entity entity) { return entity.position(); }
    public static double x(Vec3 position) { return position.x(); }
    public static double y(Vec3 position) { return position.y(); }
    public static double z(Vec3 position) { return position.z(); }
}
