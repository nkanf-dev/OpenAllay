package dev.openallay.client.gui.settings;

/** Responsive settings geometry independent from Minecraft widgets. */
@dev.openallay.value.ValueType(SettingsLayout.ValueSchemaProvider.class)
public final class SettingsLayout {
    private final boolean wide;
    private final boolean showBack;
    private final Rect header;
    private final Rect content;
    private final Rect navigation;
    private final Rect list;
    private final Rect editor;
    private final Rect footer;
    public SettingsLayout(boolean wide, boolean showBack, Rect header, Rect content, Rect navigation, Rect list, Rect editor, Rect footer) {
        this.wide = wide;
        this.showBack = showBack;
        this.header = header;
        this.content = content;
        this.navigation = navigation;
        this.list = list;
        this.editor = editor;
        this.footer = footer;
    }
    public boolean wide() { return wide; }
    public boolean showBack() { return showBack; }
    public Rect header() { return header; }
    public Rect content() { return content; }
    public Rect navigation() { return navigation; }
    public Rect list() { return list; }
    public Rect editor() { return editor; }
    public Rect footer() { return footer; }
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
public static SettingsLayout calculate(int width, int height, SettingsSection section) {
        SettingsLayout layout = calculate(width, height);
        boolean needsList = section == SettingsSection.MODELS || section == SettingsSection.EXTENSIONS
                || section == SettingsSection.SKILLS;
        if (!layout.wide() || needsList) return layout;
        Rect editor = new Rect(layout.navigation().right() + 6, layout.content().y(),
                layout.content().right() - layout.navigation().right() - 6, layout.content().height());
        Rect absent = new Rect(editor.x(), editor.y(), 0, editor.height());
        return new SettingsLayout(true, false, layout.header(), layout.content(),
                layout.navigation(), absent, editor, layout.footer());
    }
public int maximumNavigationScroll(int sectionCount) {
        return Math.max(0, 16 + sectionCount * 24 - navigation.height());
    }
public int maximumPageScroll(int contentHeight) {
        return Math.max(0, contentHeight - editor.height() + 8);
    }
public int pageOrigin(int scroll) {
        return editor.y() - Math.max(0, scroll);
    }
public boolean pageWidgetVisible(int y, int widgetHeight) {
        return y >= editor.y() && y + widgetHeight <= editor.bottom();
    }
@dev.openallay.value.ValueType(Rect.ValueSchemaProvider.class)
public static final class Rect {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    public Rect(int x, int y, int width, int height) {

            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("settings rectangle size must not be negative");
            }

        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
public int right() {
            return x + width;
        }
public int bottom() {
            return y + height;
        }
public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Rect)) return false;
        Rect that = (Rect) other;
        return x == that.x && y == that.y && width == that.width && height == that.height;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(x);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        return hash;
    }
    @Override public String toString() { return "Rect[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Rect> schema() {
            return new dev.openallay.value.ValueSchema<>(Rect.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Rect>>asList(new dev.openallay.value.ValueSchema.Component<>(Rect.class, "x", Rect::x), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "y", Rect::y), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "width", Rect::width), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "height", Rect::height)), arguments -> new Rect((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SettingsLayout)) return false;
        SettingsLayout that = (SettingsLayout) other;
        return wide == that.wide && showBack == that.showBack && java.util.Objects.equals(header, that.header) && java.util.Objects.equals(content, that.content) && java.util.Objects.equals(navigation, that.navigation) && java.util.Objects.equals(list, that.list) && java.util.Objects.equals(editor, that.editor) && java.util.Objects.equals(footer, that.footer);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(wide);
        hash = 31 * hash + Boolean.hashCode(showBack);
        hash = 31 * hash + java.util.Objects.hashCode(header);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        hash = 31 * hash + java.util.Objects.hashCode(navigation);
        hash = 31 * hash + java.util.Objects.hashCode(list);
        hash = 31 * hash + java.util.Objects.hashCode(editor);
        hash = 31 * hash + java.util.Objects.hashCode(footer);
        return hash;
    }
    @Override public String toString() { return "SettingsLayout[wide=" + wide + ", showBack=" + showBack + ", header=" + header + ", content=" + content + ", navigation=" + navigation + ", list=" + list + ", editor=" + editor + ", footer=" + footer + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SettingsLayout> schema() {
            return new dev.openallay.value.ValueSchema<>(SettingsLayout.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SettingsLayout>>asList(new dev.openallay.value.ValueSchema.Component<>(SettingsLayout.class, "wide", SettingsLayout::wide), new dev.openallay.value.ValueSchema.Component<>(SettingsLayout.class, "showBack", SettingsLayout::showBack), new dev.openallay.value.ValueSchema.Component<>(SettingsLayout.class, "header", SettingsLayout::header), new dev.openallay.value.ValueSchema.Component<>(SettingsLayout.class, "content", SettingsLayout::content), new dev.openallay.value.ValueSchema.Component<>(SettingsLayout.class, "navigation", SettingsLayout::navigation), new dev.openallay.value.ValueSchema.Component<>(SettingsLayout.class, "list", SettingsLayout::list), new dev.openallay.value.ValueSchema.Component<>(SettingsLayout.class, "editor", SettingsLayout::editor), new dev.openallay.value.ValueSchema.Component<>(SettingsLayout.class, "footer", SettingsLayout::footer)), arguments -> new SettingsLayout((Boolean) arguments[0], (Boolean) arguments[1], (Rect) arguments[2], (Rect) arguments[3], (Rect) arguments[4], (Rect) arguments[5], (Rect) arguments[6], (Rect) arguments[7]));
        }
    }
}
