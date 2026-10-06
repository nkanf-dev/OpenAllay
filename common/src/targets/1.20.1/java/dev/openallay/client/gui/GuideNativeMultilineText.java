package dev.openallay.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

/** Native editor before the builder/line-limit API; not a replacement widget. */
public final class GuideNativeMultilineText {
    private GuideNativeMultilineText() {}
    public static GuideMultilineEditor find(GuideWidget widget) {
        return find(GuideNativeWidgets.nativeWidget(widget));
    }


    public static GuideMultilineEditor create(Font font, int x, int y, int width, int height,
            Component placeholder, Component narration) {
        return new GuideNativeMultilineEditor(new MultiLineEditBox(font, x, y, width, height, placeholder, narration));
    }

    public static void tick(net.minecraft.client.gui.components.events.GuiEventListener widget) {
        if (widget instanceof MultiLineEditBox editor) editor.tick();
    }

    public static GuideMultilineEditor find(net.minecraft.client.gui.components.events.GuiEventListener widget) {
        return widget instanceof MultiLineEditBox editor ? new GuideNativeMultilineEditor(editor) : null;
    }

    public static void setValue(GuideMultilineEditor editor, String value, boolean bypassLineLimit) {
        editor.setValue(value, bypassLineLimit);
    }

    public static void setValue(MultiLineEditBox editor, String value, boolean bypassLineLimit) {
        // This native family has no line limit. Native setValue retains its character-limit behavior.
        editor.setValue(value);
    }

    public static void resize(MultiLineEditBox editor, int width, int height, int x, int y) {
        editor.setX(x);
        editor.setY(y);
        editor.setWidth(width);
        ((dev.openallay.client.gui.mixin.MultiLineEditBoxHeightAccess) editor).openallay$height(height);
    }

    public static int defaultTotalPadding() {
        // Native scroll/text-area widgets in this family use four pixels per side.
        return 8;
    }
}
