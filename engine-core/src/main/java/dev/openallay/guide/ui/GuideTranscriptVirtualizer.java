package dev.openallay.guide.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure variable-height row index with binary-search visibility and stable anchors. */
public final class GuideTranscriptVirtualizer {
    @dev.openallay.value.ValueType(Row.ValueSchemaProvider.class)
public static final class Row {
    private final String id;
    private final int height;
    public Row(String id, int height) {

            if (id == null || dev.openallay.util.Java8Strings.isBlank(id) || height <= 0) {
                throw new IllegalArgumentException("virtual row identity and height are required");
            }

        this.id = id;
        this.height = height;
    }
    public String id() { return id; }
    public int height() { return height; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Row)) return false;
        Row that = (Row) other;
        return java.util.Objects.equals(id, that.id) && height == that.height;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + Integer.hashCode(height);
        return hash;
    }
    @Override public String toString() { return "Row[id=" + id + ", height=" + height + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Row> schema() {
            return new dev.openallay.value.ValueSchema<>(Row.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Row>>asList(new dev.openallay.value.ValueSchema.Component<>(Row.class, "id", Row::id), new dev.openallay.value.ValueSchema.Component<>(Row.class, "height", Row::height)), arguments -> new Row((String) arguments[0], (Integer) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Window.ValueSchemaProvider.class)
public static final class Window {
    private final int fromIndex;
    private final int toIndexExclusive;
    private final int totalHeight;
    public Window(int fromIndex, int toIndexExclusive, int totalHeight) {

            if (fromIndex < 0 || toIndexExclusive < fromIndex || totalHeight < 0) {
                throw new IllegalArgumentException("invalid virtual window");
            }

        this.fromIndex = fromIndex;
        this.toIndexExclusive = toIndexExclusive;
        this.totalHeight = totalHeight;
    }
    public int fromIndex() { return fromIndex; }
    public int toIndexExclusive() { return toIndexExclusive; }
    public int totalHeight() { return totalHeight; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Window)) return false;
        Window that = (Window) other;
        return fromIndex == that.fromIndex && toIndexExclusive == that.toIndexExclusive && totalHeight == that.totalHeight;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(fromIndex);
        hash = 31 * hash + Integer.hashCode(toIndexExclusive);
        hash = 31 * hash + Integer.hashCode(totalHeight);
        return hash;
    }
    @Override public String toString() { return "Window[fromIndex=" + fromIndex + ", toIndexExclusive=" + toIndexExclusive + ", totalHeight=" + totalHeight + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Window> schema() {
            return new dev.openallay.value.ValueSchema<>(Window.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Window>>asList(new dev.openallay.value.ValueSchema.Component<>(Window.class, "fromIndex", Window::fromIndex), new dev.openallay.value.ValueSchema.Component<>(Window.class, "toIndexExclusive", Window::toIndexExclusive), new dev.openallay.value.ValueSchema.Component<>(Window.class, "totalHeight", Window::totalHeight)), arguments -> new Window((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2]));
        }
    }
}

    private List<Row> rows = dev.openallay.util.Java8Collections.listOf();
    private int[] offsets = {0};
    private Map<String, Integer> indexes = dev.openallay.util.Java8Collections.mapOf();

    public void update(List<Row> replacement) {
        replacement = dev.openallay.util.Java8Collections.listCopyOf(replacement);
        LinkedHashMap<String, Integer> nextIndexes = new LinkedHashMap<>();
        int[] nextOffsets = new int[replacement.size() + 1];
        for (int index = 0; index < replacement.size(); index++) {
            Row row = replacement.get(index);
            if (nextIndexes.put(row.id(), index) != null) {
                throw new IllegalArgumentException("duplicate virtual row ID " + row.id());
            }
            nextOffsets[index + 1] = Math.addExact(nextOffsets[index], row.height());
        }
        rows = replacement;
        offsets = nextOffsets;
        indexes = java.util.Collections.unmodifiableMap(nextIndexes);
    }

    public Window visible(int scroll, int viewportHeight, int overscanPixels) {
        if (scroll < 0 || viewportHeight < 0 || overscanPixels < 0) {
            throw new IllegalArgumentException("viewport dimensions must not be negative");
        }
        if (rows.isEmpty()) return new Window(0, 0, 0);
        int top = Math.max(0, scroll - overscanPixels);
        int bottom = Math.min(totalHeight(), Math.addExact(scroll, viewportHeight + overscanPixels));
        int from = rowAt(top);
        int to = bottom >= totalHeight() ? rows.size() : Math.min(rows.size(), rowAt(bottom) + 1);
        return new Window(from, to, totalHeight());
    }

    public GuideViewportAnchor anchorAt(int scroll) {
        if (rows.isEmpty()) return null;
        int clamped = Math.max(0, Math.min(scroll, Math.max(0, totalHeight() - 1)));
        int index = rowAt(clamped);
        return new GuideViewportAnchor(rows.get(index).id(), clamped - offsets[index]);
    }

    public int restore(GuideViewportAnchor anchor, int fallbackScroll, int viewportHeight) {
        if (anchor == null || !indexes.containsKey(anchor.rowId())) {
            return clampScroll(fallbackScroll, viewportHeight);
        }
        int index = indexes.get(anchor.rowId());
        return clampScroll(offsets[index] + anchor.pixelOffset(), viewportHeight);
    }

    public boolean atBottom(int scroll, int viewportHeight) {
        return scroll >= maximumScroll(viewportHeight) - 1;
    }

    public int maximumScroll(int viewportHeight) {
        return Math.max(0, totalHeight() - Math.max(0, viewportHeight));
    }

    public int clampScroll(int scroll, int viewportHeight) {
        return Math.max(0, Math.min(scroll, maximumScroll(viewportHeight)));
    }

    public int offset(int rowIndex) {
        if (rowIndex < 0 || rowIndex > rows.size()) throw new IndexOutOfBoundsException("Index out of range: " + rowIndex);
        return offsets[rowIndex];
    }

    public int totalHeight() {
        return offsets[offsets.length - 1];
    }

    public List<Row> rows() {
        return rows;
    }

    private int rowAt(int pixel) {
        int low = 0;
        int high = rows.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (pixel < offsets[middle]) {
                high = middle - 1;
            } else if (pixel >= offsets[middle + 1]) {
                low = middle + 1;
            } else {
                return middle;
            }
        }
        return Math.max(0, Math.min(rows.size() - 1, low));
    }
}
