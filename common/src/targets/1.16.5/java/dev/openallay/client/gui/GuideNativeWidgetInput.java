package dev.openallay.client.gui;

import java.util.Objects;
import net.minecraft.client.gui.components.events.GuiEventListener;

/** Typed adapter for native listeners; the selected binding still owns callbacks and focus transitions. */
public final class GuideNativeWidgetInput implements GuideWidgetInput {
    private final GuiEventListener widget;
    public GuideNativeWidgetInput(GuiEventListener widget) { this.widget = Objects.requireNonNull(widget, "widget"); }
    @Override public boolean guideKeyPressed(GuideInputKey event) { return GuideNativeInput.keyPressed(widget, event); }
    @Override public boolean guideKeyReleased(GuideInputKey event) { return GuideNativeInput.keyReleased(widget, event); }
    @Override public boolean guideCharTyped(GuideInputCharacter event) { return GuideNativeInput.charTyped(widget, event); }
    @Override public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) { return GuideNativeInput.mouseClicked(widget, event, doubleClick); }
    @Override public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) { return GuideNativeInput.mouseDragged(widget, event, dx, dy); }
    @Override public boolean guideMouseReleased(GuideInputMouse event) { return GuideNativeInput.mouseReleased(widget, event); }
    @Override public void guideSetFocused(boolean focused) {
        if (!focused) {
            GuideNativeInput.releaseTextFocus(widget);
        } else if (widget instanceof net.minecraft.client.gui.components.AbstractWidget nativeWidget) {
            ((GuideNativeFocusAccess) nativeWidget).openallay$guideFocus(true);
        } else {
            throw new IllegalStateException("Focus acquisition requires a native widget, not a container path");
        }
    }
    @Override public boolean guideIsFocused() {
        if (widget instanceof net.minecraft.client.gui.components.AbstractWidget nativeWidget) return nativeWidget.isFocused();
        if (widget instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) return container.getFocused() != null;
        return false;
    }
}
