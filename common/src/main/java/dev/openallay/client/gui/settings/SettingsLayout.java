package dev.openallay.client.gui.settings;

/** Responsive settings geometry independent from Minecraft widgets. */
public record SettingsLayout(
        boolean wide,
        boolean showBack,
        Rect header,
        Rect content,
        Rect navigation,
        Rect list,
        Rect editor,
        Rect footer) {
    private static final int WIDE_THRESHOLD = 700;

    public static SettingsLayout calculate(int width, int height) {
        if (width < 240 || height < 180) {
            throw new IllegalArgumentException("settings screen is too small");
        }
        int margin = 12;
        Rect header = new Rect(margin, 8, width - margin * 2, 28);
        Rect content = new Rect(margin, 42, width - margin * 2, height - 102);
        Rect footer = new Rect(margin, content.bottom(), width - margin * 2, 52);
        boolean wide = width >= WIDE_THRESHOLD;
        if (!wide) {
            Rect absent = new Rect(content.x(), content.y(), 0, content.height());
            return new SettingsLayout(
                    false, true, header, content, absent, absent, content, footer);
        }
        int gap = 6;
        int navigationWidth = 142;
        int listWidth = Math.min(230, Math.max(176, content.width() / 4));
        Rect navigation = new Rect(
                content.x(), content.y(), navigationWidth, content.height());
        Rect list = new Rect(
                navigation.right() + gap, content.y(), listWidth, content.height());
        Rect editor = new Rect(
                list.right() + gap,
                content.y(),
                content.right() - list.right() - gap,
                content.height());
        return new SettingsLayout(
                true, false, header, content, navigation, list, editor, footer);
    }

    /** Scroll calculations use only the editor viewport; the footer is never part of the page. */
    public int maximumPageScroll(int contentHeight) {
        return Math.max(0, contentHeight - editor.height() + 8);
    }

    public int pageOrigin(int scroll) {
        return editor.y() - Math.max(0, scroll);
    }

    public boolean pageWidgetVisible(int y, int widgetHeight) {
        return y >= editor.y() && y + widgetHeight <= editor.bottom();
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("settings rectangle size must not be negative");
            }
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }
}
