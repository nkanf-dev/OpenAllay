package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.hud.GuideNativeToastBinding;
import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forward the native manager's finished animation fact for OpenAllay-owned toasts only. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public abstract class ToastInstanceCompletionMixin {
    @Shadow @Final private Toast toast;
    @Unique private boolean openallay$completionReported;

    @Inject(method = "hasFinishedRendering", at = @At("RETURN"))
    private void openallay$completed(CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && !openallay$completionReported
                && toast instanceof GuideNativeToastBinding owned) {
            openallay$completionReported = true;
            owned.onFinishedRendering();
        }
    }
}
