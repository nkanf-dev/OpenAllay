package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.hud.GuideNativeToastBinding;
import dev.openallay.client.gui.hud.GuideToastSlotManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native true means HIDE completed; the manager commits completion after its null array store. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class ToastInstanceCompletionMixin {
    @Shadow @Final private Toast toast;
    // The real render invocation supplies its manager; synthetic outer field names are not an API.
    @org.spongepowered.asm.mixin.Unique private ToastComponent openallay$renderManager;

    // The only Toast.height invocation in render is bytecode 97, multiplied by nativeIndex at 102.
    @Redirect(method = "render(IILcom/mojang/blaze3d/vertex/PoseStack;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/Toast;height()I"),
            require = 1, expect = 1, allow = 1)
    private int openallay$physicalSlotHeight(Toast renderedToast) {
        return renderedToast instanceof GuideNativeToastBinding owned
                ? owned.nativeSlotHeight() : renderedToast.height();
    }

    @Redirect(method = "render(IILcom/mojang/blaze3d/vertex/PoseStack;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/Toast;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/components/toasts/ToastComponent;J)Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;"),
            require = 1, expect = 1, allow = 1)
    private Toast.Visibility openallay$renderAndObserve(Toast renderedToast, PoseStack graphics,
                                                       ToastComponent manager, long elapsed) {
        openallay$renderManager = manager;
        try {
            if (Boolean.getBoolean("openallay.e2e.enabled")) {
                float top;
                try (org.lwjgl.system.MemoryStack memory = org.lwjgl.system.MemoryStack.stackPush()) {
                    var matrix = memory.mallocFloat(16);
                    org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, matrix);
                    top = matrix.get(13);
                }
                ((dev.openallay.guide.e2e.GuideNativeEditorE2EProbe.ToastReadback) manager)
                        .openallay$observePaint(renderedToast, top);
            }
            return renderedToast.render(graphics, manager, elapsed);
        } catch (RuntimeException | Error failure) {
            openallay$renderManager = null;
            throw failure;
        }
    }

    @Inject(method = "render(IILcom/mojang/blaze3d/vertex/PoseStack;)Z", at = @At("RETURN"),
            require = 1, expect = 1, allow = 1)
    private void openallay$stageRemoval(int guiWidth, int nativeIndex, PoseStack graphics,
                                      CallbackInfoReturnable<Boolean> callback) {
        ToastComponent manager = openallay$renderManager;
        openallay$renderManager = null;
        if (callback.getReturnValueZ()) {
            if (manager == null) throw new IllegalStateException("Native toast completed without its render manager");
            ((GuideToastSlotManager) manager).openallay$stageNativeRemoval(toast, nativeIndex);
        }
    }
}
