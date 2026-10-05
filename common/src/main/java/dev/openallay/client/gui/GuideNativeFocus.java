package dev.openallay.client.gui;

import net.minecraft.client.gui.screens.Screen;

/** Native focus-path clearing only; text-owner retirement stays in GuideTextInputFocus. */
public final class GuideNativeFocus {
    private GuideNativeFocus() {}

    public static void clear(Screen screen) {
        screen.clearFocus();
    }
}
