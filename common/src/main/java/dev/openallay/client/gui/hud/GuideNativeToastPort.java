package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.presentation.GuideNotificationPort;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** Adds only OpenAllay-owned native toasts. Never clears the native manager. */
public final class GuideNativeToastPort implements GuideNotificationPort {
    private final Minecraft minecraft;
    private GuideNativeToast lastOwnedToast;

    public GuideNativeToastPort(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override public Handle show(Notification notification) {
        GuideNativeToast toast = new GuideNativeToast(notification, minecraft.font, minecraft.getWindow().getGuiScaledWidth());
        MinecraftClientWindow.toastManager(minecraft).addToast(toast);
        toast.queued();
        lastOwnedToast = toast;
        return toast;
    }

    /** Null until the last owned toast completes a real native extraction. No show, render or I/O side effects. */
    public GuideNativeToast.Receipt e2eReceipt() {
        return lastOwnedToast == null ? null : lastOwnedToast.e2eReceipt();
    }
}
