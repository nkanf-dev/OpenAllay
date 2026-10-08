package dev.openallay.client.gui;

/** Actual legacy product widgets implement their contract directly; no native alias or Object payload. */
public final class GuideNativeWidgets {
    private GuideNativeWidgets() {}
    public static GuideWidget wrap(GuideWidget widget) { return java.util.Objects.requireNonNull(widget, "widget"); }
}
