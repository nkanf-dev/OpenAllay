package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.hud.GuideNativeToastBinding;
import dev.openallay.client.gui.hud.GuideToastSlotManager;
import dev.openallay.client.gui.hud.GuideToastSlotReservations;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import dev.openallay.guide.e2e.GuideNativeEditorE2EProbe;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends native admission only; the native five-element instance array remains sparse. */
@Mixin(ToastComponent.class)
public abstract class ToastComponentSlotMixin implements GuideToastSlotManager, GuideNativeEditorE2EProbe.ToastReadback {
    @Unique private final GuideToastSlotReservations<Toast> openallay$slots =
            new GuideToastSlotReservations<>(new Toast[5]);
    @Unique private int openallay$nativeIndex;
    @Unique private int openallay$pendingSlot = -1;
    @Unique private Toast openallay$pendingRemoval;
    @Shadow @Final private Deque<Toast> queued;
    @Unique private final Map<Toast, Float> openallay$tops = new IdentityHashMap<>();
    @Unique private final Map<Toast, Integer> openallay$removals = new IdentityHashMap<>();
    @Override public void openallay$observePaint(Toast toast, float top) {
        if (Boolean.getBoolean("openallay.e2e.enabled")) openallay$tops.put(toast, top);
    }
    @Override public GuideNativeEditorE2EProbe.ToastSnapshot openallay$snapshot() {
        List<Boolean> occupied = java.util.stream.IntStream.range(0, 5)
                .mapToObj(slot -> !openallay$slots.canReserve(slot, 1)).toList();
        return new GuideNativeEditorE2EProbe.ToastSnapshot(List.copyOf(queued), occupied,
                Map.copyOf(openallay$tops), Map.copyOf(openallay$removals), openallay$pendingRemoval == null);
    }

    // Native local 2 is initialized at 15; its first LOAD (ordinal 0) is loop condition bytecode 16.
    @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "LOAD", ordinal = 0), index = 2, require = 1, expect = 1, allow = 1)
    private int openallay$observeNativeIndex(int nativeIndex) {
        openallay$nativeIndex = nativeIndex;
        return nativeIndex;
    }

    // One isEmpty site at 75 and one removeFirst site at 97. A blocked head stays in place.
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Deque;isEmpty()Z", remap = false),
            require = 1, expect = 1, allow = 1)
    private boolean openallay$admissionUnavailable(Deque<Toast> queue) {
        if (queue.isEmpty()) return true;
        return !openallay$slots.canReserve(openallay$nativeIndex, openallay$slotCount(queue.peekFirst()));
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Deque;removeFirst()Ljava/lang/Object;", remap = false),
            require = 1, expect = 1, allow = 1)
    private Object openallay$reserveAdmittedHead(Deque<Toast> queue) {
        // Object is the exact JDK removeFirst erasure, not an untyped native handle; the value is Toast.
        Toast admitted = queue.removeFirst();
        openallay$slots.reserve(admitted, openallay$nativeIndex, openallay$slotCount(admitted));
        return admitted;
    }

    @Unique private static int openallay$slotCount(Toast toast) {
        return toast instanceof GuideNativeToastBinding owned ? owned.nativeSlotCount() : 1;
    }

    @Override public void openallay$stageNativeRemoval(Toast toast, int firstSlot) {
        openallay$pendingRemoval = toast;
        openallay$pendingSlot = firstSlot;
    }

    // Fourth visible GETFIELD: 18(length),26(load),56(null store),63(post-store admission read).
    // Native true reaches AASTORE 61 before this instruction. False skips the staging call.
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;visible:[Lnet/minecraft/client/gui/components/toasts/ToastComponent$ToastInstance;",
                    opcode = 180, ordinal = 3), require = 1, expect = 1, allow = 1)
    private void openallay$commitNativeRemoval(PoseStack graphics, CallbackInfo callback) {
        Toast removed = openallay$pendingRemoval;
        if (removed == null || openallay$pendingSlot != openallay$nativeIndex) return;
        int firstSlot = openallay$pendingSlot;
        openallay$pendingRemoval = null;
        openallay$pendingSlot = -1;
        if (openallay$slots.release(removed, firstSlot)) {
            if (Boolean.getBoolean("openallay.e2e.enabled")) openallay$removals.merge(removed, 1, Integer::sum);
            if (removed instanceof GuideNativeToastBinding owned) owned.onFinishedRendering();
        }
    }

    @Inject(method = "clear()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void openallay$nativeClear(CallbackInfo callback) {
        openallay$slots.clear();
        openallay$tops.clear();
        openallay$removals.clear();
        openallay$pendingRemoval = null;
        openallay$pendingSlot = -1;
    }
}
