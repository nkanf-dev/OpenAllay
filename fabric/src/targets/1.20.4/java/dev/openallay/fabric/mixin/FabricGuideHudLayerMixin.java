package dev.openallay.fabric.mixin;

import dev.openallay.fabric.FabricNativeHudRegistration;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual 1.20.1-1.20.4 chat invocation, inside the native HUD visibility branch. */
@Mixin(Gui.class)
public abstract class FabricGuideHudLayerMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;III)V"))
    private void openallay$beforeChat(GuiGraphics graphics, float partialTick, CallbackInfo callback) {
        FabricNativeHudRegistration.beforeChat(graphics);
    }
}
