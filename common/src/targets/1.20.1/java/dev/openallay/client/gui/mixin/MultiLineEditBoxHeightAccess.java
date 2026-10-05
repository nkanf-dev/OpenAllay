package dev.openallay.client.gui.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.20.1 native widgets have a height field but no height setter; preserve the live editor. */
@Mixin(AbstractWidget.class)
public interface MultiLineEditBoxHeightAccess {
    @Accessor("height") void openallay$height(int height);
}
