package dev.openallay.client.gui;

import dev.openallay.client.gui.mixin.MultilineTextFieldAccessor;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;

/** Text operations delegate to the live native widget; input, rendering and IME stay native. */
public final class GuideNativeMultilineEditor implements GuideMultilineEditor {
    private final MultiLineEditBox nativeEditor;
    private final GuideWidget guideWidget;
    public GuideNativeMultilineEditor(MultiLineEditBox nativeEditor) {
        this.nativeEditor = Objects.requireNonNull(nativeEditor);
        guideWidget = GuideNativeWidgets.wrap(nativeEditor);
    }
    @Override public GuideWidget widget() { return guideWidget; }
    @Override public String getValue() { return nativeEditor.getValue(); }
    @Override public void setValue(String value, boolean bypassLineLimit) {
        GuideNativeMultilineText.setValue(nativeEditor, value, bypassLineLimit);
    }
    @Override public void setValueListener(Consumer<String> listener) {
        nativeEditor.setValueListener(listener);
    }
    @Override public void setCharacterLimit(int limit) { nativeEditor.setCharacterLimit(limit); }
    @Override public void resize(int width, int height, int x, int y) {
        GuideNativeMultilineText.resize(nativeEditor, width, height, x, y);
        NativeAccess widget = (NativeAccess) nativeEditor;
        MultilineTextFieldAccessor field = (MultilineTextFieldAccessor) widget.openallay$textField();
        int contentWidth = GuideComposerGeometry.contentWidth(width, widget.openallay$totalInnerPadding());
        if (field.openallay$width() != contentWidth) {
            field.openallay$width(contentWidth);
            field.openallay$reflowDisplayLines();
            widget.openallay$refreshScrollAmount();
        }
    }
    /** Exact native mixin bridge; absent text types never enter the shared feature contract. */
    public interface NativeAccess {
        MultilineTextField openallay$textField();
        int openallay$totalInnerPadding();
        void openallay$refreshScrollAmount();
    }
}
