package dev.openallay.client.context;

import net.minecraft.world.inventory.Slot;

/** Live native slot geometry; implemented by the selected container-screen binding. */
public interface GuideNativeSlotHitTest {
    Slot openallay$getHoveredSlot(double x, double y);
}
