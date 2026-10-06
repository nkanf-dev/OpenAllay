package dev.openallay.client.context;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
public final class MinecraftMenuFacts {
    private MinecraftMenuFacts() {}
    public static int stateId(Container menu) { return -1; }
    public static String stateDiagnostic(Container menu) { return "state_id_unavailable;menu_type_registry_unavailable"; }
    public static ItemStack carried(Minecraft client,Container menu) { return client.player.inventory.getItemStack(); }
    public static int containerSlot(Slot slot) { return slot.getSlotIndex(); }
}
