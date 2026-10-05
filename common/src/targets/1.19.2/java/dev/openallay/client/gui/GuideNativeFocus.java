package dev.openallay.client.gui;

import net.minecraft.client.gui.screens.Screen;

/** Retire the native child focus and drag ownership before the shared text owner retires. */
public final class GuideNativeFocus {
    private GuideNativeFocus() {}
    public static void clear(Screen screen) {
        var focused = screen.getFocused();
        if (focused != null) GuideNativeInput.releaseTextFocus(focused);
        screen.setFocused(null);
        screen.setDragging(false);
    }
}
