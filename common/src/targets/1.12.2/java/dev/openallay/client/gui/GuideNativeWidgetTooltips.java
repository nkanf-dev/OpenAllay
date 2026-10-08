package dev.openallay.client.gui;

/** Actual selected button tooltip intent; native rendering scope owns deferred draw. */
public final class GuideNativeWidgetTooltips {
    private GuideNativeWidgetTooltips() {}
    public static void set(GuideNativeEditBox widget, GuideTooltip tooltip) { widget.setTooltip(tooltip); }
    public static void set(GuideNativeButton widget, GuideTooltip tooltip) { widget.setTooltip(tooltip); }
}
