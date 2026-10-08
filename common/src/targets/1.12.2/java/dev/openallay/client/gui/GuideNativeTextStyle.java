package dev.openallay.client.gui;

import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

/** Actual legacy text palette. Native 1.12.2 Style has no arbitrary RGB TextColor. */
public final class GuideNativeTextStyle {
    private GuideNativeTextStyle() {}
    public static Style bold(Style style, boolean value) { return style.createShallowCopy().setBold(value); }
    public static Style color(Style style, int rgb) {
        int[] palette = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
                0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
        int nearest = 0;
        long distance = Long.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int dr = (rgb >> 16 & 255) - (palette[i] >> 16 & 255);
            int dg = (rgb >> 8 & 255) - (palette[i] >> 8 & 255);
            int db = (rgb & 255) - (palette[i] & 255);
            long candidate = dr * dr + dg * dg + db * db;
            if (candidate < distance) { distance = candidate; nearest = i; }
        }
        return style.createShallowCopy().setColor(TextFormatting.fromColorIndex(nearest));
    }
}
