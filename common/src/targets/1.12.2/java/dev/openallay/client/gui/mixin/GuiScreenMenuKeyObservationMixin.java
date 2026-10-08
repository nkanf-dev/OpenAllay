package dev.openallay.client.gui.mixin;
import dev.openallay.client.observation.GuideNativeMenuKeyObservation;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Normal native event-loop admission after Forge Pre cancellation, before the actual callback. */
@Mixin(GuiScreen.class)
public abstract class GuiScreenMenuKeyObservationMixin {
    @Inject(method="handleInput()V", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiScreen;handleKeyboardInput()V"), require=1)
    private void openallay$before(CallbackInfo callback) {
        GuideNativeMenuKeyObservation.before((GuiScreen)(Object)this);
    }
}
