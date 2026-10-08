package dev.openallay.client.gui.mixin;
import dev.openallay.client.observation.MinecraftClientViewCapture;
import dev.openallay.client.observation.GuideNativeCameraFov;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Exact MCP renderer frame entry and native world/post-GUI boundaries. */
@Mixin(EntityRenderer.class)
public abstract class GameRendererObservationMixin {
    @Inject(method="updateCameraAndRender(FJ)V", at=@At("HEAD"), require=1)
    private void openallay$begin(float partialTick, long nanoTime, CallbackInfo callback) {
        ((GuideNativeCameraFov) Minecraft.getMinecraft().entityRenderer).openallay$beginFrame();
    }
    @Inject(method="updateCameraAndRender(FJ)V", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V", shift=At.Shift.AFTER), require=1)
    private void openallay$world(float partialTick, long nanoTime, CallbackInfo callback) {
        MinecraftClientViewCapture.beforeGui(Minecraft.getMinecraft(), true);
    }
    @Inject(method="updateCameraAndRender(FJ)V", at=@At("RETURN"), require=1)
    private void openallay$gui(float partialTick, long nanoTime, CallbackInfo callback) {
        MinecraftClientViewCapture.afterGui(Minecraft.getMinecraft(), true);
    }
}
