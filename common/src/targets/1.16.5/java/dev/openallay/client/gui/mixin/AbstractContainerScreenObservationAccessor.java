package dev.openallay.client.gui.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

/** Native live geometry and slot order, not last-painted hoveredSlot or private invoker. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenObservationAccessor implements dev.openallay.client.context.GuideNativeSlotHitTest {
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected abstract boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY);
    public final Slot openallay$getHoveredSlot(double x, double y) {
        for (Slot slot : menu.slots) {
            if (slot.isActive() && isHovering(slot.x, slot.y, 16, 16, x, y)) return slot;
        }
        return null;
    }
}
