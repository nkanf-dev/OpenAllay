package dev.openallay.client.gui;

import net.minecraft.client.gui.GuiScreen;

/** Actual text owner retirement without stopping a replacement screen's input. */
public final class GuideTextInputFocus {
    private GuideTextInputFocus() {}
    public static boolean isTextFocused(GuiScreen screen) {
        if (!(screen instanceof GuideNativeScreenCallbacks guide)) return false;
        GuideWidgetInput focused = guide.getGuideFocused();
        return focused instanceof GuideNativeEditBox || focused instanceof GuidePrimitiveMultilineEditor;
    }
    public static void release(GuiScreen screen) {
        if (screen instanceof GuideNativeScreenCallbacks guide) guide.clearGuideFocus();
    }
}
