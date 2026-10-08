package dev.openallay.client.gui;

import java.util.Objects;

/** Typed native-neutral adapter; GuiTextField leaf supplies the actual widget callbacks. */
public final class GuideNativeWidgetInput implements GuideWidgetInput {
    private final GuideWidgetInput widget;
    public GuideNativeWidgetInput(GuideWidgetInput widget) { this.widget = Objects.requireNonNull(widget, "widget"); }
    @Override public boolean guideKeyPressed(GuideInputKey event) { return widget.guideKeyPressed(event); }
    @Override public boolean guideKeyReleased(GuideInputKey event) { return widget.guideKeyReleased(event); }
    @Override public boolean guideCharTyped(GuideInputCharacter event) { return widget.guideCharTyped(event); }
    @Override public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) { return widget.guideMouseClicked(event, doubleClick); }
    @Override public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) { return widget.guideMouseDragged(event, dx, dy); }
    @Override public boolean guideMouseReleased(GuideInputMouse event) { return widget.guideMouseReleased(event); }
    @Override public void guideSetFocused(boolean focused) { widget.guideSetFocused(focused); }
    @Override public boolean guideIsFocused() { return widget.guideIsFocused(); }
}
