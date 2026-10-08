package dev.openallay.client.gui.mixin;
import dev.openallay.client.observation.GuideNativeMenuKeyObservation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observe actual allowUserInput callback; consumed state is unavailable, so no shortcut is routed. */
@Mixin(Minecraft.class)
public abstract class KeyboardHandlerObservationMixin {
    @Unique private GuiScreen openallay$dispatchScreen;
    @Inject(method="runTickKeyboard()V", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiScreen;handleKeyboardInput()V"), require=1)
    private void openallay$before(CallbackInfo callback) {
        openallay$dispatchScreen = ((Minecraft)(Object)this).currentScreen;
    }
    @Inject(method="runTickKeyboard()V", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiScreen;handleKeyboardInput()V", shift=At.Shift.AFTER), require=1)
    private void openallay$after(CallbackInfo callback) {
        GuideNativeMenuKeyObservation.afterAllowUserInput((Minecraft)(Object)this, openallay$dispatchScreen);
        openallay$dispatchScreen = null;
    }
}
