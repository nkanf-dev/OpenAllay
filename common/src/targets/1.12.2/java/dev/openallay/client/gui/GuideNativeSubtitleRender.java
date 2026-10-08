package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/** The actual active overlay keeps its sound-listener state; no replacement instance is created. */
public final class GuideNativeSubtitleRender {
    private GuideNativeSubtitleRender() {}
    public static void render(Minecraft client, GuideGraphics graphics) {
        ((dev.openallay.client.gui.mixin.GuiSubtitleAccess) client.ingameGUI)
                .openallay$subtitleOverlay().renderSubtitles(new ScaledResolution(client));
    }
}
