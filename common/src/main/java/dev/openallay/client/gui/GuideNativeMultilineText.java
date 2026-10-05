package dev.openallay.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

/** Native editor construction and full-text replacement; the live editor stays native. */
public final class GuideNativeMultilineText {
    private GuideNativeMultilineText() {}

    public static GuideMultilineEditor create(Font font, int x, int y, int width, int height,
            Component placeholder, Component narration) {
        return new GuideNativeMultilineEditor(MultiLineEditBox.builder().setX(x).setY(y).setPlaceholder(placeholder)
                .build(font, width, height, narration));
    }

    public static GuideMultilineEditor find(net.minecraft.client.gui.components.events.GuiEventListener widget) {
        return widget instanceof MultiLineEditBox editor ? new GuideNativeMultilineEditor(editor) : null;
    }

    public static void setValue(GuideMultilineEditor editor, String value, boolean bypassLineLimit) {
        editor.setValue(value, bypassLineLimit);
    }

    public static void setValue(MultiLineEditBox editor, String value, boolean bypassLineLimit) {
        editor.setValue(value, bypassLineLimit);
    }

    public static void resize(MultiLineEditBox editor, int width, int height, int x, int y) {
        editor.setRectangle(width, height, x, y);
    }

    public static int defaultTotalPadding() {
        return AbstractTextAreaWidget.DEFAULT_TOTAL_PADDING;
    }
}
