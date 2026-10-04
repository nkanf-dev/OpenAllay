package dev.openallay.client.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;

/** Retire only this screen's native text owners; never stop a replacement screen's input. */
public final class GuideTextInputFocus {
    private GuideTextInputFocus() {}

    public static void release(Screen screen) {
        screen.clearFocus();
        for (var child : screen.children()) {
            if (child instanceof EditBox || child instanceof MultiLineEditBox) {
                GuideNativeInput.releaseTextFocus(child);
            }
        }
    }
}
