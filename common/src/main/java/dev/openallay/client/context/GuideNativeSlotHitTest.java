package dev.openallay.client.context;



/** Live native slot geometry; implemented by the selected container-screen binding. */
public interface GuideNativeSlotHitTest {
    net.minecraft.world.inventory.Slot openallay$getHoveredSlot(double x, double y);
}
