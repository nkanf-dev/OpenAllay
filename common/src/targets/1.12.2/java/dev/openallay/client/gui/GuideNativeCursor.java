package dev.openallay.client.gui;

/** LWJGL 2 keeps the current native cursor owner. It has no stock resize-all system cursor ABI. */
final class GuideNativeCursor {
    private GuideNativeCursor() {}
    static void requestResize() { }
}
