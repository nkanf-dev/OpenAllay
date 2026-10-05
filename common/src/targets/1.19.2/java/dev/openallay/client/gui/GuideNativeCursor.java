package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;

/** Earlier callback canvases retain the native window cursor owner. */
final class GuideNativeCursor {
    private GuideNativeCursor() {}
    static void requestResize(PoseStack graphics) { GuideLegacyCursor.requestResize(); }
}
