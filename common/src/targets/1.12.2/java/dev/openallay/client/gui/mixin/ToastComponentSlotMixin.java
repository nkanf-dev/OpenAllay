package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.hud.GuideNativeToastBinding;
import dev.openallay.client.gui.hud.GuideToastSlotManager;
import dev.openallay.client.gui.hud.GuideToastSlotReservations;
import java.util.Deque;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.gui.toasts.GuiToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends native admission only; the native five-element instance array remains sparse. */
@Mixin(GuiToast.class)
public abstract class ToastComponentSlotMixin implements GuideToastSlotManager {
    @Unique private final GuideToastSlotReservations<IToast> openallay$slots =
            new GuideToastSlotReservations<>(new IToast[5]);
    @Unique private int openallay$nativeIndex;
    @Unique private int openallay$pendingSlot = -1;
    @Unique private IToast openallay$pendingRemoval;
    @Shadow @Final private Deque<IToast> toastsQueue;
    // Actual drawToast source uses native local 2 as the visible-array loop index.
    @ModifyVariable(method = "drawToast(Lnet/minecraft/client/gui/ScaledResolution;)V",
            at = @At(value = "LOAD", ordinal = 0), index = 2, require = 1, expect = 1, allow = 1)
    private int openallay$observeNativeIndex(int nativeIndex) {
        openallay$nativeIndex = nativeIndex;
        return nativeIndex;
    }

    // Actual drawToast has one native queue-admission site. A blocked head stays in place.
    @Redirect(method = "drawToast(Lnet/minecraft/client/gui/ScaledResolution;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Deque;isEmpty()Z", remap = false),
            require = 1, expect = 1, allow = 1)
    private boolean openallay$admissionUnavailable(Deque<IToast> queue) {
        if (queue.isEmpty()) return true;
        return !openallay$slots.canReserve(openallay$nativeIndex, openallay$slotCount(queue.peekFirst()));
    }

    @Redirect(method = "drawToast(Lnet/minecraft/client/gui/ScaledResolution;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Deque;removeFirst()Ljava/lang/Object;", remap = false),
            require = 1, expect = 1, allow = 1)
    private Object openallay$reserveAdmittedHead(Deque<IToast> queue) {
        // Object is the exact JDK removeFirst erasure, not an untyped native handle; the value is IToast.
        IToast admitted = queue.removeFirst();
        openallay$slots.reserve(admitted, openallay$nativeIndex, openallay$slotCount(admitted));
        return admitted;
    }

    @Unique private static int openallay$slotCount(IToast toast) {
        return toast instanceof GuideNativeToastBinding owned ? owned.slotCount() : 1;
    }

    @Override public void openallay$stageNativeRemoval(IToast toast, int firstSlot) {
        openallay$pendingRemoval = toast;
        openallay$pendingSlot = firstSlot;
    }

    // Fourth visible read occurs after the native removal null-store and before admission.
    @Inject(method = "drawToast(Lnet/minecraft/client/gui/ScaledResolution;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/gui/toasts/GuiToast;visible:[Lnet/minecraft/client/gui/toasts/GuiToast$ToastInstance;",
                    opcode = 180, ordinal = 3), require = 1, expect = 1, allow = 1)
    private void openallay$commitNativeRemoval(net.minecraft.client.gui.ScaledResolution resolution, CallbackInfo callback) {
        IToast removed = openallay$pendingRemoval;
        if (removed == null || openallay$pendingSlot != openallay$nativeIndex) return;
        int firstSlot = openallay$pendingSlot;
        openallay$pendingRemoval = null;
        openallay$pendingSlot = -1;
        if (openallay$slots.release(removed, firstSlot)) {
            if (removed instanceof GuideNativeToastBinding owned) owned.onFinishedRendering();
        }
    }

    @Inject(method = "clear()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void openallay$nativeClear(CallbackInfo callback) {
        openallay$slots.clear();
        openallay$pendingRemoval = null;
        openallay$pendingSlot = -1;
    }
}
