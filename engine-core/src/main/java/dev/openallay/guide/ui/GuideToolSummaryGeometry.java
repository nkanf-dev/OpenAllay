package dev.openallay.guide.ui;

import java.util.ArrayList;
import java.util.List;

/** One measured compact row shared by paint, native child hits, and virtualization. */
@dev.openallay.value.ValueType(GuideToolSummaryGeometry.ValueSchemaProvider.class)
public final class GuideToolSummaryGeometry {
    private final GuideUiLayout.Rect card;
    private final GuideUiLayout.Rect icon;
    private final GuideUiLayout.Rect title;
    private final GuideUiLayout.Rect status;
    private final GuideUiLayout.Rect description;
    private final List<GuideUiLayout.Rect> capsules;
    private final int rowHeight;
    public GuideToolSummaryGeometry(GuideUiLayout.Rect card, GuideUiLayout.Rect icon, GuideUiLayout.Rect title, GuideUiLayout.Rect status, GuideUiLayout.Rect description, List<GuideUiLayout.Rect> capsules, int rowHeight) {
 capsules = dev.openallay.util.Java8Collections.listCopyOf(capsules);
        this.card = card;
        this.icon = icon;
        this.title = title;
        this.status = status;
        this.description = description;
        this.capsules = capsules;
        this.rowHeight = rowHeight;
    }
    public GuideUiLayout.Rect card() { return card; }
    public GuideUiLayout.Rect icon() { return icon; }
    public GuideUiLayout.Rect title() { return title; }
    public GuideUiLayout.Rect status() { return status; }
    public GuideUiLayout.Rect description() { return description; }
    public List<GuideUiLayout.Rect> capsules() { return capsules; }
    public int rowHeight() { return rowHeight; }
public static final int SINGLE_LINE_HEIGHT = 28;
public static final int DESCRIPTION_HEIGHT = 40;
private static final int PADDING = 6;
private static final int GAP = 4;
public static GuideToolSummaryGeometry measure(int x, int y, int width, int statusWidth,
            List<Integer> capsuleWidths, boolean description, int spacing) {
        if (width < 40 || statusWidth < 0 || spacing < 0
                || capsuleWidths.stream().anyMatch(value -> value < 22)) {
            throw new IllegalArgumentException("invalid Tool summary geometry");
        }
        int available = width - PADDING * 2;
        int badgeWidth = Math.min(statusWidth, Math.max(1, available / 3));
        int titleMinimum = Math.max(1, Math.min(72, available / 3));
        int remaining = available - 10 - GAP - titleMinimum - GAP - badgeWidth;
        List<Integer> fitted = new ArrayList<>();
        for (int capsuleWidth : capsuleWidths) {
            if (capsuleWidth + GAP > remaining) break;
            fitted.add(capsuleWidth);
            remaining -= capsuleWidth + GAP;
        }
        int capsuleTotal = fitted.stream().mapToInt(value -> value + GAP).sum();
        int titleWidth = Math.max(1, available - 10 - GAP * 2 - badgeWidth - capsuleTotal);
        int titleX = x + PADDING + 10 + GAP;
        int statusX = titleX + titleWidth + GAP;
        int capsuleX = statusX + badgeWidth + GAP;
        List<GuideUiLayout.Rect> bounds = new ArrayList<>();
        for (int capsuleWidth : fitted) {
            bounds.add(new GuideUiLayout.Rect(capsuleX, y + PADDING, capsuleWidth, 16));
            capsuleX += capsuleWidth + GAP;
        }
        int height = description ? DESCRIPTION_HEIGHT : SINGLE_LINE_HEIGHT;
        return new GuideToolSummaryGeometry(
                new GuideUiLayout.Rect(x, y, width, height),
                new GuideUiLayout.Rect(x + PADDING, y + PADDING + 3, 10, 10),
                new GuideUiLayout.Rect(titleX, y + PADDING + 3, titleWidth, 10),
                new GuideUiLayout.Rect(statusX, y + PADDING + 3, badgeWidth, 10),
                new GuideUiLayout.Rect(titleX, y + PADDING + 18, width - PADDING - (titleX - x), description ? 10 : 0),
                bounds, height + spacing);
    }
public Hit hit(double mouseX, double mouseY) {
        for (int index = 0; index < capsules.size(); index++) {
            if (capsules.get(index).contains(mouseX, mouseY)) return new Hit(Kind.CAPSULE, index);
        }
        return new Hit(card.contains(mouseX, mouseY) ? Kind.DETAIL : Kind.OUTSIDE, -1);
    }
public enum Kind { CAPSULE, DETAIL, OUTSIDE }
@dev.openallay.value.ValueType(Hit.ValueSchemaProvider.class)
public static final class Hit {
    private final Kind kind;
    private final int capsuleIndex;
    public Hit(Kind kind, int capsuleIndex) {
        this.kind = kind;
        this.capsuleIndex = capsuleIndex;
    }
    public Kind kind() { return kind; }
    public int capsuleIndex() { return capsuleIndex; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Hit)) return false;
        Hit that = (Hit) other;
        return java.util.Objects.equals(kind, that.kind) && capsuleIndex == that.capsuleIndex;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + Integer.hashCode(capsuleIndex);
        return hash;
    }
    @Override public String toString() { return "Hit[kind=" + kind + ", capsuleIndex=" + capsuleIndex + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Hit> schema() {
            return new dev.openallay.value.ValueSchema<>(Hit.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Hit>>asList(new dev.openallay.value.ValueSchema.Component<>(Hit.class, "kind", Hit::kind), new dev.openallay.value.ValueSchema.Component<>(Hit.class, "capsuleIndex", Hit::capsuleIndex)), arguments -> new Hit((Kind) arguments[0], (Integer) arguments[1]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideToolSummaryGeometry)) return false;
        GuideToolSummaryGeometry that = (GuideToolSummaryGeometry) other;
        return java.util.Objects.equals(card, that.card) && java.util.Objects.equals(icon, that.icon) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(capsules, that.capsules) && rowHeight == that.rowHeight;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(card);
        hash = 31 * hash + java.util.Objects.hashCode(icon);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(capsules);
        hash = 31 * hash + Integer.hashCode(rowHeight);
        return hash;
    }
    @Override public String toString() { return "GuideToolSummaryGeometry[card=" + card + ", icon=" + icon + ", title=" + title + ", status=" + status + ", description=" + description + ", capsules=" + capsules + ", rowHeight=" + rowHeight + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideToolSummaryGeometry> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideToolSummaryGeometry.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideToolSummaryGeometry>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideToolSummaryGeometry.class, "card", GuideToolSummaryGeometry::card), new dev.openallay.value.ValueSchema.Component<>(GuideToolSummaryGeometry.class, "icon", GuideToolSummaryGeometry::icon), new dev.openallay.value.ValueSchema.Component<>(GuideToolSummaryGeometry.class, "title", GuideToolSummaryGeometry::title), new dev.openallay.value.ValueSchema.Component<>(GuideToolSummaryGeometry.class, "status", GuideToolSummaryGeometry::status), new dev.openallay.value.ValueSchema.Component<>(GuideToolSummaryGeometry.class, "description", GuideToolSummaryGeometry::description), new dev.openallay.value.ValueSchema.Component<>(GuideToolSummaryGeometry.class, "capsules", GuideToolSummaryGeometry::capsules), new dev.openallay.value.ValueSchema.Component<>(GuideToolSummaryGeometry.class, "rowHeight", GuideToolSummaryGeometry::rowHeight)), arguments -> new GuideToolSummaryGeometry((GuideUiLayout.Rect) arguments[0], (GuideUiLayout.Rect) arguments[1], (GuideUiLayout.Rect) arguments[2], (GuideUiLayout.Rect) arguments[3], (GuideUiLayout.Rect) arguments[4], (List) arguments[5], (Integer) arguments[6]));
        }
    }
}
