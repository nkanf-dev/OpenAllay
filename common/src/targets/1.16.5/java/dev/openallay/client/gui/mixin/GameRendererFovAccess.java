package dev.openallay.client.gui.mixin;

import dev.openallay.client.observation.GuideNativeCameraFov;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Capture the actual native return after Forge modifies it, without a private invoker. */
@Mixin(GameRenderer.class)
public abstract class GameRendererFovAccess implements GuideNativeCameraFov {
    @Unique private Camera openallay$camera;
    @Unique private net.minecraft.client.multiplayer.ClientLevel openallay$level;
    @Unique private double openallay$fov;
    @Unique private long openallay$frame;
    @Unique private long openallay$observedFrame = -1;
    @Override public final void openallay$beginFrame() { openallay$frame++; }
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)D", at = @At("RETURN"), require = 1)
    private void openallay$captureFov(Camera camera, float partialTick, boolean useSetting, CallbackInfoReturnable<Double> callback) {
        if (useSetting) {
            openallay$camera = camera;
            openallay$level = Minecraft.getInstance().level;
            openallay$fov = callback.getReturnValueD();
            openallay$observedFrame = openallay$frame;
        }
    }
    @Override public final double openallay$observedFov(Camera camera) {
        if (openallay$observedFrame != openallay$frame || camera != openallay$camera || openallay$level == null || Minecraft.getInstance().level != openallay$level) {
            throw new IllegalStateException("Native camera FOV has not been rendered for this world");
        }
        return openallay$fov;
    }
}
