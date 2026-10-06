package dev.openallay.client.gui;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Actual native color value, not an integer overload that old Style does not expose. */
public final class GuideNativeTextStyle {
    private GuideNativeTextStyle() {}
    public static Style color(Style style, int rgb) { return style.withColor(TextColor.fromRgb(rgb)); }
}
