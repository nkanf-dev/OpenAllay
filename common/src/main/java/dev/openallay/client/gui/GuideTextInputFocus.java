package dev.openallay.client.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** Retire only this screen's native text owners; never stop a replacement screen's input. */
public final class GuideTextInputFocus {
    private GuideTextInputFocus() {}
    public static boolean isTextFocused(Screen screen) {
        return screen != null && (screen.getFocused() instanceof EditBox
                || GuideNativeMultilineText.find(screen.getFocused()) != null);
    }

    public static void release(Screen screen) {
        GuideNativeFocus.clear(screen);
        for (var child : screen.children()) {
            if (child instanceof EditBox || GuideNativeMultilineText.find(child) != null) {
                GuideWidgetInputs.releaseTextFocus(GuideNativeInput.widgetInput(child));
            }
        }
    }
}
