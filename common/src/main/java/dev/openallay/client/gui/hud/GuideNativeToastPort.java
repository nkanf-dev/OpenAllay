package dev.openallay.client.gui.hud;

import dev.openallay.client.presentation.GuideNotificationPort;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** Adds only OpenAllay-owned native toasts. Never clears the native manager. */
public final class GuideNativeToastPort implements GuideNotificationPort {
    private final Minecraft minecraft;

    public GuideNativeToastPort(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override public Handle show(Notification notification) {
        GuideNativeToast toast = new GuideNativeToast(notification);
        minecraft.gui.toastManager().addToast(toast);
        return toast;
    }
}
