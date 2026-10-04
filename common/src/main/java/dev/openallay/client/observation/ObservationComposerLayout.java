package dev.openallay.client.observation;

import dev.openallay.guide.ui.GuideUiLayout;

/** One bounded observation row. It shares short strips with existing image previews. */
public record ObservationComposerLayout(GuideUiLayout.Rect row, GuideUiLayout.Rect remaining) {
    public static ObservationComposerLayout calculate(GuideUiLayout.Rect strip, boolean otherImages) {
        if (strip.width() == 0 || strip.height() == 0) return new ObservationComposerLayout(GuideUiLayout.Rect.EMPTY, strip);
        int rowHeight = Math.min(12, strip.height());
        boolean separateRow = strip.height() >= 26;
        int rowWidth = separateRow || !otherImages ? strip.width() : Math.min(strip.width(), Math.max(96, strip.width() / 2));
        var row = new GuideUiLayout.Rect(strip.x(), strip.y(), rowWidth, rowHeight);
        var remaining = separateRow ? new GuideUiLayout.Rect(strip.x(), strip.y() + rowHeight + 2,
                strip.width(), strip.height() - rowHeight - 2)
                : new GuideUiLayout.Rect(Math.min(strip.right(), row.right() + 2), strip.y(),
                        Math.max(0, strip.right() - row.right() - 2), strip.height());
        return new ObservationComposerLayout(row, remaining);
    }
}
