package dev.openallay.fabric.mixin;

import dev.openallay.fabric.FabricNativeHudRegistration;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual pre-DeltaTracker CHAT layer: same native HUD order and visibility, real float callback. */
@Mixin(Gui.class)
public abstract class FabricGuideHudLayerMixin {
    @Inject(method = "renderChat", at = @At("HEAD"))
    private void openallay$beforeChat(GuiGraphics graphics, float partialTick, CallbackInfo callback) {
        FabricNativeHudRegistration.beforeChat(graphics);
    }
}
