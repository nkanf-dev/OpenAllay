package dev.openallay.client.gui;

/** Product geometry resolves to actual selected owners, never an AbstractWidget alias. */
public final class GuideNativeWidgetGeometry {
    private GuideNativeWidgetGeometry() {}
    public static int x(GuideWidget widget) { return widget.getX(); }
    public static int y(GuideWidget widget) { return widget.getY(); }
    public static void x(GuideWidget widget, int x) { widget.setX(x); }
}
