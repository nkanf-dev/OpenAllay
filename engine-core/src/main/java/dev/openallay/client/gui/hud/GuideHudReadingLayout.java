package dev.openallay.client.gui.hud;

import dev.openallay.guide.ui.GuideUiLayout;

/** One measured compact result viewport with separate, non-overlapping footer strips. */
@dev.openallay.value.ValueType(GuideHudReadingLayout.ValueSchemaProvider.class)
public final class GuideHudReadingLayout {
    private final GuideUiLayout.Rect results;
    private final GuideUiLayout.Rect notice;
    private final GuideUiLayout.Rect navigation;
    private final GuideUiLayout.Rect composer;
    private final GuideUiLayout.Rect actions;
    private final GuideUiLayout.Rect scrollbar;
    private final boolean footerFits;
    public GuideHudReadingLayout(GuideUiLayout.Rect results, GuideUiLayout.Rect notice, GuideUiLayout.Rect navigation, GuideUiLayout.Rect composer, GuideUiLayout.Rect actions, GuideUiLayout.Rect scrollbar, boolean footerFits) {
        this.results = results;
        this.notice = notice;
        this.navigation = navigation;
        this.composer = composer;
        this.actions = actions;
        this.scrollbar = scrollbar;
        this.footerFits = footerFits;
    }
    public GuideUiLayout.Rect results() { return results; }
    public GuideUiLayout.Rect notice() { return notice; }
    public GuideUiLayout.Rect navigation() { return navigation; }
    public GuideUiLayout.Rect composer() { return composer; }
    public GuideUiLayout.Rect actions() { return actions; }
    public GuideUiLayout.Rect scrollbar() { return scrollbar; }
    public boolean footerFits() { return footerFits; }
public static GuideHudReadingLayout calculate(int x, int y, int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("reading card dimensions must not be negative");
        int inner = Math.max(0, width - 16);
        // A tiny viewport cannot fit the complete native composer. It must not expose a negative
        // result viewport or paint footer rows outside the card; zero-height strips remain absent.
        int resultTop = Math.min(height, 36);
        int resultBottom = Math.max(resultTop, height - 108);
        dev.openallay.guide.ui.GuideUiLayout.Rect results = new GuideUiLayout.Rect(x + Math.min(8, width), y + resultTop,
                Math.max(0, inner - 8), resultBottom - resultTop);
        dev.openallay.guide.ui.GuideUiLayout.Rect notice = strip(x, y, width, height, height - 104, 10);
        dev.openallay.guide.ui.GuideUiLayout.Rect navigation = strip(x, y, width, height, height - 92, 14);
        dev.openallay.guide.ui.GuideUiLayout.Rect composer = strip(x, y, width, height, height - 76, 38);
        dev.openallay.guide.ui.GuideUiLayout.Rect actions = strip(x, y, width, height, height - 30, 20);
        dev.openallay.guide.ui.GuideUiLayout.Rect scrollbar = new GuideUiLayout.Rect(x + Math.max(0, width - 12), results.y(),
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHudReadingLayout)) return false;
        GuideHudReadingLayout that = (GuideHudReadingLayout) other;
        return java.util.Objects.equals(results, that.results) && java.util.Objects.equals(notice, that.notice) && java.util.Objects.equals(navigation, that.navigation) && java.util.Objects.equals(composer, that.composer) && java.util.Objects.equals(actions, that.actions) && java.util.Objects.equals(scrollbar, that.scrollbar) && footerFits == that.footerFits;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(results);
        hash = 31 * hash + java.util.Objects.hashCode(notice);
        hash = 31 * hash + java.util.Objects.hashCode(navigation);
        hash = 31 * hash + java.util.Objects.hashCode(composer);
        hash = 31 * hash + java.util.Objects.hashCode(actions);
        hash = 31 * hash + java.util.Objects.hashCode(scrollbar);
        hash = 31 * hash + Boolean.hashCode(footerFits);
        return hash;
    }
    @Override public String toString() { return "GuideHudReadingLayout[results=" + results + ", notice=" + notice + ", navigation=" + navigation + ", composer=" + composer + ", actions=" + actions + ", scrollbar=" + scrollbar + ", footerFits=" + footerFits + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHudReadingLayout> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHudReadingLayout.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHudReadingLayout>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHudReadingLayout.class, "results", GuideHudReadingLayout::results), new dev.openallay.value.ValueSchema.Component<>(GuideHudReadingLayout.class, "notice", GuideHudReadingLayout::notice), new dev.openallay.value.ValueSchema.Component<>(GuideHudReadingLayout.class, "navigation", GuideHudReadingLayout::navigation), new dev.openallay.value.ValueSchema.Component<>(GuideHudReadingLayout.class, "composer", GuideHudReadingLayout::composer), new dev.openallay.value.ValueSchema.Component<>(GuideHudReadingLayout.class, "actions", GuideHudReadingLayout::actions), new dev.openallay.value.ValueSchema.Component<>(GuideHudReadingLayout.class, "scrollbar", GuideHudReadingLayout::scrollbar), new dev.openallay.value.ValueSchema.Component<>(GuideHudReadingLayout.class, "footerFits", GuideHudReadingLayout::footerFits)), arguments -> new GuideHudReadingLayout((GuideUiLayout.Rect) arguments[0], (GuideUiLayout.Rect) arguments[1], (GuideUiLayout.Rect) arguments[2], (GuideUiLayout.Rect) arguments[3], (GuideUiLayout.Rect) arguments[4], (GuideUiLayout.Rect) arguments[5], (Boolean) arguments[6]));
        }
    }
}
