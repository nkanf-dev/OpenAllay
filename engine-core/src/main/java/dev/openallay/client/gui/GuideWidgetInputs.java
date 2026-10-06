package dev.openallay.client.gui;

import java.util.Objects;

/** Shared input-only operations. Native bindings supply actual widget owners. */
public final class GuideWidgetInputs {
    private GuideWidgetInputs() {}
    public static boolean keyPressed(GuideWidgetInput widget, GuideInputKey event) {
        return Objects.requireNonNull(widget, "widget").guideKeyPressed(event);
    }
    public static boolean keyReleased(GuideWidgetInput widget, GuideInputKey event) {
        return Objects.requireNonNull(widget, "widget").guideKeyReleased(event);
    }
    public static boolean charTyped(GuideWidgetInput widget, GuideInputCharacter event) {
        return Objects.requireNonNull(widget, "widget").guideCharTyped(event);
    }
    public static boolean mouseClicked(GuideWidgetInput widget, GuideInputMouse event, boolean doubleClick) {
        return Objects.requireNonNull(widget, "widget").guideMouseClicked(event, doubleClick);
    }
    public static boolean mouseDragged(GuideWidgetInput widget, GuideInputMouse event, double dx, double dy) {
        return Objects.requireNonNull(widget, "widget").guideMouseDragged(event, dx, dy);
    }
    public static boolean mouseReleased(GuideWidgetInput widget, GuideInputMouse event) {
        return Objects.requireNonNull(widget, "widget").guideMouseReleased(event);
    }
    public static void releaseTextFocus(GuideWidgetInput widget) {
        Objects.requireNonNull(widget, "widget").guideSetFocused(false);
    }
}
