package dev.openallay.fabric.mixin;

import dev.openallay.fabric.FabricNativeHudRegistration;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual vanilla CHAT layer inherits the native hidden-HUD condition and paint order. */
@Mixin(Gui.class)
public abstract class FabricGuideHudLayerMixin {
    @Inject(method = "renderChat", at = @At("HEAD"))
    private void openallay$beforeChat(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
        FabricNativeHudRegistration.beforeChat(graphics);
    }
}
