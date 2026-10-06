package dev.openallay.client.gui;

import java.util.Objects;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Typed actual widget adapter. Geometry/state writes stay on the one native owner. */
public final class GuideNativeWidgets {
    private GuideNativeWidgets() {}
    private record Widget(AbstractWidget nativeWidget) implements GuideWidget {
        private Widget { Objects.requireNonNull(nativeWidget, "nativeWidget"); }
        @Override public int getX() { return GuideNativeWidgetGeometry.x(nativeWidget); }
        @Override public int getY() { return GuideNativeWidgetGeometry.y(nativeWidget); }
        @Override public int getWidth() { return nativeWidget.getWidth(); }
        @Override public int getHeight() { return nativeWidget.getHeight(); }
        @Override public void setX(int x) { GuideNativeWidgetGeometry.x(nativeWidget, x); }
        @Override public void setY(int y) { nativeWidget.setY(y); }
        @Override public boolean guideActive() { return nativeWidget.active; }
        @Override public void guideActive(boolean active) { nativeWidget.active = active; }
        @Override public boolean guideVisible() { return nativeWidget.visible; }
        @Override public void guideVisible(boolean visible) { nativeWidget.visible = visible; }
        @Override public Component getMessage() { return nativeWidget.getMessage(); }
        @Override public Class<?> guideNativeType() { return nativeWidget.getClass(); }
    }
    public static GuideWidget wrap(AbstractWidget widget) { return new Widget(widget); }
    public static AbstractWidget nativeWidget(GuideWidget widget) {
        if (!(widget instanceof Widget adapter)) throw new IllegalArgumentException("Widget belongs to another native binding");
        return adapter.nativeWidget();
    }
}
