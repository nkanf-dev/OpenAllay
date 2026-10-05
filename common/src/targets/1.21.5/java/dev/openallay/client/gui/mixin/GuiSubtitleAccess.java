package dev.openallay.client.gui.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Immediate Gui family: native subtitles are a field-owned overlay, not a Gui render method. */
@Mixin(Gui.class)
public interface GuiSubtitleAccess {
    @Accessor("subtitleOverlay") SubtitleOverlay openallay$subtitleOverlay();
}
