package dev.openallay.client.gui;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.screens.Screen;

/** Pre-1.20.3 Screen keeps clearFocus private; use its exact public native path operations. */
public final class GuideNativeFocus {
    private GuideNativeFocus() {}

    public static void clear(Screen screen) {
        ComponentPath path = screen.getCurrentFocusPath();
        if (path != null) {
            path.applyFocus(false);
        }
    }
}
