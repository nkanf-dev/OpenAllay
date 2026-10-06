package dev.openallay.client.gui.hud;

import net.minecraft.client.gui.toasts.IToast;

/** Native instance removal staging; the real manager commits after its native null array store. */
public interface GuideToastSlotManager {
    void openallay$stageNativeRemoval(IToast toast, int firstSlot);
}
