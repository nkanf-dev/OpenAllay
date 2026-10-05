package dev.openallay.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;

/** Native geometry access for widget-typed shared code and development probes. */
public final class GuideNativeWidgetGeometry {
    private GuideNativeWidgetGeometry() {}
    public static int x(AbstractWidget widget) { return widget.getX(); }
    public static int y(AbstractWidget widget) { return widget.getY(); }
    public static void x(AbstractWidget widget, int x) { widget.setX(x); }
}
