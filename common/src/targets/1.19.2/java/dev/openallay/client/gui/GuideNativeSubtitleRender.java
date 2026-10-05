package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;

/** Exact native subtitle render binding; the transparent screen remains shared. */
public final class GuideNativeSubtitleRender {
    private GuideNativeSubtitleRender() {}
    public static void render(Minecraft client, PoseStack graphics) {
        ((dev.openallay.client.gui.mixin.GuiSubtitleAccess) client.gui)
                .openallay$subtitleOverlay().render(graphics);
    }
}
