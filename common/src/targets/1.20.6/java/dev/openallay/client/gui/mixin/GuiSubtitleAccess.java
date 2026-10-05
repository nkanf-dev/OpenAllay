package dev.openallay.client.gui.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native subtitle ownership before DeltaTracker; the real overlay render takes only GuiGraphics. */
@Mixin(Gui.class)
public interface GuiSubtitleAccess {
    @Accessor("subtitleOverlay") SubtitleOverlay openallay$subtitleOverlay();
}
