package dev.openallay.client.gui.hud;

import net.minecraft.client.gui.components.toasts.Toast;

/** Typed link from a native instance return to its owning native manager. */
public interface GuideToastSlotManager {
    void openallay$stageNativeRemoval(Toast toast, int firstSlot);
}
