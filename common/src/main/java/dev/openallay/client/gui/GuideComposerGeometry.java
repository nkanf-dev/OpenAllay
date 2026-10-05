package dev.openallay.client.gui;

import dev.openallay.guide.ui.GuideUiLayout;

/** Resize the same native composer; reflow only on width changes, keeping cursor, selection and IME. */
public final class GuideComposerGeometry {
    private GuideComposerGeometry() {}

    public static void resize(GuideMultilineEditor composer, GuideUiLayout.Rect bounds) {
        composer.resize(bounds.width(), bounds.height(), bounds.x(), bounds.y());
    }

    static int contentWidth(int widgetWidth, int totalInnerPadding) {
        if (widgetWidth <= totalInnerPadding || totalInnerPadding < 0) {
            throw new IllegalArgumentException("Composer has no readable native content width");
        }
        return widgetWidth - totalInnerPadding;
    }
}
