package dev.openallay.client.gui.mixin;

import dev.openallay.client.observation.MinecraftClientViewCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.21/1.21.1 immediate GUI boundaries; shared capture owns the native readback. */
@Mixin(GameRenderer.class)
public abstract class GameRendererObservationMixin {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            shift = At.Shift.AFTER), require = 1)
    private void openallay$worldFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo callback) {
        MinecraftClientViewCapture.beforeGui(Minecraft.getInstance(), advanceGameTime);
    }

    // The sole flush follows HUD, Screen/overlay, saving indicator and toast drawing.
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V", ordinal = 0, shift = At.Shift.AFTER), require = 1)
    private void openallay$gameUiFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo callback) {
        MinecraftClientViewCapture.afterGui(Minecraft.getInstance(), advanceGameTime);
    }
}
