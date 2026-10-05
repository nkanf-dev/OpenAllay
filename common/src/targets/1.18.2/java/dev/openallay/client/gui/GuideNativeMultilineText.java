package dev.openallay.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/** Native input primitive binding where Minecraft has no multiline widget. */
public final class GuideNativeMultilineText {
    private GuideNativeMultilineText() {}
    public static GuideMultilineEditor create(Font font, int x, int y, int width, int height,
            Component placeholder, Component narration) {
        return new GuideNativeMultilineEditor(font, x, y, width, height, placeholder, narration);
    }
    public static GuideMultilineEditor find(GuiEventListener widget) {
        return widget instanceof GuideNativeMultilineEditor editor ? editor : null;
    }
    public static void setValue(GuideMultilineEditor editor, String value, boolean bypassLineLimit) {
        editor.setValue(value, bypassLineLimit);
    }
    public static void tick(GuiEventListener widget) {
        if (widget instanceof GuideNativeMultilineEditor editor) editor.tick();
    }
    public static int defaultTotalPadding() { return 8; }
}
