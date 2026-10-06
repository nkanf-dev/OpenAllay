package dev.openallay.client.gui.mixin;
import dev.openallay.client.context.GuideNativeSlotHitTest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Invoke the real live native slot geometry, without reimplementing hit testing. */
@Mixin(GuiContainer.class)
public interface AbstractContainerScreenObservationAccessor extends GuideNativeSlotHitTest {
    @Invoker("getSlotAtPosition") Slot openallay$slotAtPosition(int x, int y);
    @Override default Slot openallay$getHoveredSlot(double x, double y) { return openallay$slotAtPosition((int)x, (int)y); }
}
