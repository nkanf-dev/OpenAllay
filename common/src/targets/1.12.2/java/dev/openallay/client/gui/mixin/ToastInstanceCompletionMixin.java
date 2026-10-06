package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.hud.GuideNativeToastBinding;
import dev.openallay.client.gui.hud.GuideToastSlotManager;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Actual render(int,int) native geometry/removal seam; animation/sound/lifetime stay vanilla. */
@Mixin(targets = "net.minecraft.client.gui.toasts.GuiToast$ToastInstance")
public abstract class ToastInstanceCompletionMixin {
    @Shadow @Final private IToast toast;
    // Exact synthetic outer-owner field is named field_193687_a in the successful actual bytecode.
    @Shadow @Final private GuiToast field_193687_a;
    @Unique private int openallay$guiWidth;
    @Inject(method = "render(II)Z", at = @At("HEAD"))
    private void openallay$captureWidth(int guiWidth, int nativeIndex, CallbackInfoReturnable<Boolean> callback) {
        openallay$guiWidth = guiWidth;
    }
    @ModifyArgs(method = "render(II)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"), require = 1)
    private void openallay$ownedCardWidth(Args args) {
        if (toast instanceof GuideNativeToastBinding owned) {
            float nativeX = args.get(0);
            float visibility = (openallay$guiWidth - nativeX) / 160.0F;
            args.set(0, openallay$guiWidth - owned.width() * visibility);
        }
    }
    @Inject(method = "render(II)Z", at = @At("RETURN"))
    private void openallay$stageRemoval(int guiWidth, int nativeIndex, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            ((GuideToastSlotManager) field_193687_a).openallay$stageNativeRemoval(toast, nativeIndex);
        }
    }
}
