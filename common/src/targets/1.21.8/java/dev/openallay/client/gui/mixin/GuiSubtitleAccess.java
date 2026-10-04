package dev.openallay.client.gui.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Use the native subtitle overlay, keeping HUD input/editor surfaces transparent. */
@Mixin(Gui.class)
public interface GuiSubtitleAccess {
    @Invoker("renderSubtitleOverlay") void openallay$renderSubtitles(GuiGraphics graphics, DeltaTracker delta);
}
