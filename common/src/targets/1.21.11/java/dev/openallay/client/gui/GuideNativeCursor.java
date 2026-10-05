package dev.openallay.client.gui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphics;

/** Only the callback-canvas cursor ABI changes between these native families. */
final class GuideNativeCursor {
    private GuideNativeCursor() {}
    static void requestResize(GuiGraphics graphics) { graphics.requestCursor(CursorTypes.RESIZE_ALL); }
}
