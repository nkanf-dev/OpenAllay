package dev.openallay.client.gui;

import dev.openallay.client.gui.mixin.MultilineTextFieldAccessor;
import dev.openallay.guide.ui.GuideUiLayout;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;

/** Resize the same native composer; reflow only on width changes, keeping cursor, selection and IME. */
public final class GuideComposerGeometry {
    private GuideComposerGeometry() {}

    /** Bridge implemented on the actual native widget by the exact client mixin, not a mixin-class cast. */
    public interface NativeAccess {
        MultilineTextField openallay$textField();
        int openallay$totalInnerPadding();
    }

    public static void resize(MultiLineEditBox composer, GuideUiLayout.Rect bounds) {
        composer.setRectangle(bounds.width(), bounds.height(), bounds.x(), bounds.y());
        NativeAccess widget = (NativeAccess) composer;
        MultilineTextFieldAccessor field = (MultilineTextFieldAccessor) widget.openallay$textField();
        int width = contentWidth(bounds.width(), widget.openallay$totalInnerPadding());
        if (field.openallay$width() != width) {
            field.openallay$width(width);
            field.openallay$reflowDisplayLines();
            composer.refreshScrollAmount();
        }
    }

    static int contentWidth(int widgetWidth, int totalInnerPadding) {
        if (widgetWidth <= totalInnerPadding || totalInnerPadding < 0) {
            throw new IllegalArgumentException("Composer has no readable native content width");
        }
        return widgetWidth - totalInnerPadding;
    }
}
