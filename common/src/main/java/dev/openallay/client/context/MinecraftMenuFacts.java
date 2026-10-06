package dev.openallay.client.context;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Native menu metadata and cursor custody; absence remains explicit in capture diagnostics. */
public final class MinecraftMenuFacts {
    private MinecraftMenuFacts() {}
    public static int stateId(AbstractContainerMenu menu) { return menu.getStateId(); }
    public static String stateDiagnostic(AbstractContainerMenu menu) { return ""; }
    public static ItemStack carried(Minecraft client, AbstractContainerMenu menu) { return menu.getCarried(); }
    public static int containerSlot(Slot slot) { return slot.getContainerSlot(); }
}
