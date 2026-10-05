package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Exact native subtitle render binding; the transparent screen remains shared. */
public final class GuideNativeSubtitleRender {
    private GuideNativeSubtitleRender() {}
    public static void render(Minecraft client, GuiGraphics graphics) {
        ((dev.openallay.client.gui.mixin.GuiSubtitleAccess) client.gui)
                .openallay$renderSubtitles(graphics, client.getDeltaTracker());
    }
}
