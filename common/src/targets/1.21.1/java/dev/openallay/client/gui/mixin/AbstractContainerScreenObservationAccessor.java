package dev.openallay.client.gui.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Live native slot hit-test. Reading hoveredSlot would instead reuse the previous extraction. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenObservationAccessor {
    @Invoker("findSlot")
    /** Returns null when no native slot is under the pointer. */
    Slot openallay$getHoveredSlot(double x, double y);
}
