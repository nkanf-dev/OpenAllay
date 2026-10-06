package dev.openallay.client.gui.mixin;

import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiSubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact active native overlay field: MCP overlaySubtitle / SRG field_184049_t. */
@Mixin(GuiIngame.class)
public interface GuiSubtitleAccess {
    @Accessor("overlaySubtitle") GuiSubtitleOverlay openallay$subtitleOverlay();
}
