package dev.openallay.client.gui.hud;

import dev.openallay.guide.ui.GuideUiLayout;

/** One measured compact result viewport with separate, non-overlapping footer strips. */
public record GuideHudReadingLayout(
        GuideUiLayout.Rect results,
        GuideUiLayout.Rect notice,
        GuideUiLayout.Rect navigation,
        GuideUiLayout.Rect composer,
        GuideUiLayout.Rect actions,
        GuideUiLayout.Rect scrollbar,
        boolean footerFits) {
    public static GuideHudReadingLayout calculate(int x, int y, int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("reading card dimensions must not be negative");
        int inner = Math.max(0, width - 16);
        // A tiny viewport cannot fit the complete native composer. It must not expose a negative
        // result viewport or paint footer rows outside the card; zero-height strips remain absent.
        int resultTop = Math.min(height, 36);
        int resultBottom = Math.max(resultTop, height - 108);
        var results = new GuideUiLayout.Rect(x + Math.min(8, width), y + resultTop,
                Math.max(0, inner - 8), resultBottom - resultTop);
        var notice = strip(x, y, width, height, height - 104, 10);
        var navigation = strip(x, y, width, height, height - 92, 14);
        var composer = strip(x, y, width, height, height - 76, 38);
        var actions = strip(x, y, width, height, height - 30, 20);
        var scrollbar = new GuideUiLayout.Rect(x + Math.max(0, width - 12), results.y(),
                Math.min(5, width), results.height());
        return new GuideHudReadingLayout(results, notice, navigation, composer, actions, scrollbar,
                width >= 32 && height >= 144);
    }

    private static GuideUiLayout.Rect strip(int x, int y, int width, int height, int top, int size) {
        int clippedTop = Math.max(0, Math.min(height, top));
        int clippedBottom = Math.max(clippedTop, Math.min(height, top + size));
        return new GuideUiLayout.Rect(x + Math.min(8, width), y + clippedTop,
                Math.max(0, width - 16), clippedBottom - clippedTop);
    }
}
