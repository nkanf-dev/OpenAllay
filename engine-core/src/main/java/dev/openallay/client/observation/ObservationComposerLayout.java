package dev.openallay.client.observation;

import dev.openallay.guide.ui.GuideUiLayout;

/** One bounded observation row. It shares short strips with existing image previews. */
@dev.openallay.value.ValueType(ObservationComposerLayout.ValueSchemaProvider.class)
public final class ObservationComposerLayout {
    private final GuideUiLayout.Rect row;
    private final GuideUiLayout.Rect remaining;
    public ObservationComposerLayout(GuideUiLayout.Rect row, GuideUiLayout.Rect remaining) {
        this.row = row;
        this.remaining = remaining;
    }
    public GuideUiLayout.Rect row() { return row; }
    public GuideUiLayout.Rect remaining() { return remaining; }
public static ObservationComposerLayout calculate(GuideUiLayout.Rect strip, boolean otherImages) {
        if (strip.width() == 0 || strip.height() == 0) return new ObservationComposerLayout(GuideUiLayout.Rect.EMPTY, strip);
        int rowHeight = Math.min(12, strip.height());
        boolean separateRow = strip.height() >= 26;
        int rowWidth = separateRow || !otherImages ? strip.width() : Math.min(strip.width(), Math.max(96, strip.width() / 2));
        dev.openallay.guide.ui.GuideUiLayout.Rect row = new GuideUiLayout.Rect(strip.x(), strip.y(), rowWidth, rowHeight);
        dev.openallay.guide.ui.GuideUiLayout.Rect remaining = separateRow ? new GuideUiLayout.Rect(strip.x(), strip.y() + rowHeight + 2,
                strip.width(), strip.height() - rowHeight - 2)
                : new GuideUiLayout.Rect(Math.min(strip.right(), row.right() + 2), strip.y(),
                        Math.max(0, strip.right() - row.right() - 2), strip.height());
        return new ObservationComposerLayout(row, remaining);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ObservationComposerLayout)) return false;
        ObservationComposerLayout that = (ObservationComposerLayout) other;
        return java.util.Objects.equals(row, that.row) && java.util.Objects.equals(remaining, that.remaining);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(row);
        hash = 31 * hash + java.util.Objects.hashCode(remaining);
        return hash;
    }
    @Override public String toString() { return "ObservationComposerLayout[row=" + row + ", remaining=" + remaining + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ObservationComposerLayout> schema() {
            return new dev.openallay.value.ValueSchema<>(ObservationComposerLayout.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ObservationComposerLayout>>asList(new dev.openallay.value.ValueSchema.Component<>(ObservationComposerLayout.class, "row", ObservationComposerLayout::row), new dev.openallay.value.ValueSchema.Component<>(ObservationComposerLayout.class, "remaining", ObservationComposerLayout::remaining)), arguments -> new ObservationComposerLayout((GuideUiLayout.Rect) arguments[0], (GuideUiLayout.Rect) arguments[1]));
        }
    }
}
