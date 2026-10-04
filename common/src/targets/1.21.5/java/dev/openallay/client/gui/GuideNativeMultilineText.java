package dev.openallay.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

/** Native editor before the builder/line-limit API; not a replacement widget. */
public final class GuideNativeMultilineText {
    private GuideNativeMultilineText() {}

    public static MultiLineEditBox create(Font font, int x, int y, int width, int height,
            Component placeholder, Component narration) {
        return new MultiLineEditBox(font, x, y, width, height, placeholder, narration);
    }

    public static void setValue(MultiLineEditBox editor, String value, boolean bypassLineLimit) {
        // This native family has no line limit. Native setValue retains its character-limit behavior.
        editor.setValue(value);
    }

    public static int defaultTotalPadding() {
        // Native scroll/text-area widgets in this family use four pixels per side.
        return 8;
    }
}
