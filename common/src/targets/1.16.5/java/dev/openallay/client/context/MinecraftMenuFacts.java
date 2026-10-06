package dev.openallay.client.context;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Forge36 has no menu revision token; cursor custody belongs to the real player inventory. */
public final class MinecraftMenuFacts {
    private MinecraftMenuFacts() {}
    public static int stateId(AbstractContainerMenu menu) { return -1; }
    public static String stateDiagnostic(AbstractContainerMenu menu) { return "state_id_unavailable"; }
    public static ItemStack carried(Minecraft client, AbstractContainerMenu menu) {
        return dev.openallay.context.minecraft.MinecraftPlayerFacts.inventory(client.player).getCarried();
    }
    public static int containerSlot(Slot slot) { return slot.getSlotIndex(); }
}
