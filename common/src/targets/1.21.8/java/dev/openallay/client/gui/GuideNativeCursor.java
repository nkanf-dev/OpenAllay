package dev.openallay.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/** Earlier callback canvases retain the native window cursor owner. */
final class GuideNativeCursor {
    private GuideNativeCursor() {}
    static void requestResize(GuiGraphics graphics) { GuideLegacyCursor.requestResize(); }
}
