package dev.openallay.client.gui.mixin;
import dev.openallay.client.observation.GuideNativeMenuKeyObservation;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Observe the real native text owner participating in this dispatch; no widget-tree guesses. */
@Mixin(GuiTextField.class)
public abstract class GuiTextFieldMenuKeyObservationMixin {
    @Inject(method="textboxKeyTyped(CI)Z", at=@At("HEAD"), require=1)
    private void openallay$focus(char character, int key, CallbackInfoReturnable<Boolean> callback) {
        GuideNativeMenuKeyObservation.textField(((GuiTextField)(Object)this).isFocused());
    }
}
