package dev.openallay.client.gui.mixin;

import dev.openallay.client.observation.MinecraftClientViewCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 26.3 native boundaries; admission uses the state extracted for this frame. */
@Mixin(GameRenderer.class)
public abstract class GameRendererObservationMixin {
    @Shadow @Final private GameRenderState gameRenderState;

    @Inject(method = "render()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void openallay$worldFrame(CallbackInfo callback) {
        MinecraftClientViewCapture.beforeGui(Minecraft.getInstance(), gameRenderState.shouldRenderLevel);
    }

    @Inject(method = "render()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.AFTER))
    private void openallay$gameUiFrame(CallbackInfo callback) {
        MinecraftClientViewCapture.afterGui(Minecraft.getInstance(), gameRenderState.shouldRenderLevel);
    }
}
