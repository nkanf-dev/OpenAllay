package dev.openallay.client.gui;

/** Native focus owner retirement for the actual 1.12 callback seam. */
public final class GuideNativeFocus {
    private GuideNativeFocus() {}
    public static void clear(GuideNativeScreenCallbacks screen) { screen.clearGuideFocus(); }
}
