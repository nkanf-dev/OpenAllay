package dev.openallay.client.gui.mixin;

import dev.openallay.client.observation.MinecraftClientViewCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact old render-level boundary; frame routing and image lifetime stay shared. */
@Mixin(GameRenderer.class)
public abstract class GameRendererObservationMixin {
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            shift = At.Shift.AFTER))
    private void openallay$worldFrame(float partialTick, long nanoTime, boolean advanceGameTime, CallbackInfo callback) {
        MinecraftClientViewCapture.beforeGui(Minecraft.getInstance(), advanceGameTime);
    }
    @Inject(method = "render(FJZ)V", at = @At("RETURN"))
    private void openallay$gameUiFrame(float partialTick, long nanoTime, boolean advanceGameTime, CallbackInfo callback) {
        MinecraftClientViewCapture.afterGui(Minecraft.getInstance(), advanceGameTime);
    }
}
