package dev.openallay.client.gui.mixin;

import dev.openallay.client.observation.MinecraftClientViewCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20.1 native render has one final GUI flush; capture ownership stays shared. */
@Mixin(GameRenderer.class)
public abstract class GameRendererObservationMixin {
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            shift = At.Shift.AFTER))
    private void openallay$worldFrame(float partialTick, long nanoTime, boolean advanceGameTime, CallbackInfo callback) {
        MinecraftClientViewCapture.beforeGui(Minecraft.getInstance(), advanceGameTime);
    }

    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V", ordinal = 0, shift = At.Shift.AFTER))
    private void openallay$gameUiFrame(float partialTick, long nanoTime, boolean advanceGameTime, CallbackInfo callback) {
        MinecraftClientViewCapture.afterGui(Minecraft.getInstance(), advanceGameTime);
    }
}
