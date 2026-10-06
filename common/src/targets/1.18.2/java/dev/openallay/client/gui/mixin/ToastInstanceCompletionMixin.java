package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.hud.GuideNativeToastBinding;
import dev.openallay.client.gui.hud.GuideToastSlotManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native true means HIDE completed; the manager commits completion after its null array store. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class ToastInstanceCompletionMixin {
    @Shadow @Final private Toast toast;
    @Unique private GuideToastSlotManager openallay$slotManager;

    @Inject(method = "<init>(Lnet/minecraft/client/gui/components/toasts/ToastComponent;Lnet/minecraft/client/gui/components/toasts/Toast;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void openallay$bindManager(ToastComponent component, Toast toast, CallbackInfo callback) {
        openallay$slotManager = (GuideToastSlotManager) component;
    }

    // The only Toast.height invocation in render is bytecode 107, multiplied by nativeIndex at 112.
    @Redirect(method = "render(IILcom/mojang/blaze3d/vertex/PoseStack;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/Toast;height()I"),
            require = 1, expect = 1, allow = 1)
    private int openallay$physicalSlotHeight(Toast renderedToast) {
        return renderedToast instanceof GuideNativeToastBinding owned
                ? owned.nativeSlotHeight() : renderedToast.height();
    }

    @Inject(method = "render(IILcom/mojang/blaze3d/vertex/PoseStack;)Z", at = @At("RETURN"),
            require = 1, expect = 1, allow = 1)
    private void openallay$stageRemoval(int guiWidth, int nativeIndex, PoseStack graphics,
                                      CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) openallay$slotManager.openallay$stageNativeRemoval(toast, nativeIndex);
    }
}
