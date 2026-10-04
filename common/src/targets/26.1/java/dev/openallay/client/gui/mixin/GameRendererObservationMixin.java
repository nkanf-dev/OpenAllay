package dev.openallay.client.gui.mixin;

import dev.openallay.client.observation.MinecraftClientViewCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 26.1 native boundaries. The displayed Screen is never replaced or re-extracted. */
@Mixin(GameRenderer.class)
public abstract class GameRendererObservationMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void openallay$worldFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo callback) {
        MinecraftClientViewCapture.beforeGui(Minecraft.getInstance(), advanceGameTime && Minecraft.getInstance().isGameLoadFinished());
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", shift = At.Shift.AFTER))
    private void openallay$gameUiFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo callback) {
        MinecraftClientViewCapture.afterGui(Minecraft.getInstance(), advanceGameTime && Minecraft.getInstance().isGameLoadFinished());
    }
}
