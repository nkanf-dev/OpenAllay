package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.GuidePoseScissor;
import net.minecraft.client.gui.GuiComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native scroll widgets enter balanced parent-aware scissors rather than removing a caller's clip. */
@Mixin(GuiComponent.class)
public abstract class GuiComponentScissorMixin {
    @Inject(method = "enableScissor(IIII)V", at = @At("HEAD"), cancellable = true)
    private static void openallay$enter(int left, int top, int right, int bottom, CallbackInfo callback) {
        if (!dev.openallay.client.gui.GuideNativeGraphics.isGuidePaintActive()) return;
        GuidePoseScissor.nativeScreen(left, top, right, bottom);
        callback.cancel();
    }
    @Inject(method = "disableScissor()V", at = @At("HEAD"), cancellable = true)
    private static void openallay$exit(CallbackInfo callback) {
        if (!dev.openallay.client.gui.GuideNativeGraphics.isGuidePaintActive()) return;
        GuidePoseScissor.disable();
        callback.cancel();
    }
}
