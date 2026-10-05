package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.hud.GuideNativeToastBinding;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact 1.18.2 RETURN signature; native return/removal semantics await same-job body evidence. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class ToastInstanceCompletionMixin {
    @Shadow @Final private Toast toast;
    @Unique private boolean openallay$completionReported;

    @Inject(method = "render(IILcom/mojang/blaze3d/vertex/PoseStack;)Z", at = @At("RETURN"))
    private void openallay$completed(int guiWidth, int nativeIndex, PoseStack graphics, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && !openallay$completionReported
                && toast instanceof GuideNativeToastBinding owned) {
            openallay$completionReported = true;
            owned.onFinishedRendering();
        }
    }
}
