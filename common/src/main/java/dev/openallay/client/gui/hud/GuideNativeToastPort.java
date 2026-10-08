package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.presentation.GuideNotificationPort;
import java.util.Objects;


/** Adds only OpenAllay-owned native toasts. Never clears the native manager. */
public final class GuideNativeToastPort implements GuideNotificationPort {
    private final net.minecraft.client.Minecraft minecraft;
    private GuideNativeToast lastOwnedToast;

    public GuideNativeToastPort(net.minecraft.client.Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override public Handle show(Notification notification) {
        GuideNativeToast toast = new GuideNativeToast(notification, GuideNativeToastAccess.font(minecraft), GuideNativeToastAccess.guiWidth(minecraft));
        GuideNativeToastAccess.add(minecraft, toast);
        toast.queued();
        lastOwnedToast = toast;
        return toast;
    }

    /** Null until the last owned toast completes a real native extraction. No show, render or I/O side effects. */
    public GuideNativeToast.Receipt e2eReceipt() {
        return lastOwnedToast == null ? null : lastOwnedToast.e2eReceipt();
    }
}
