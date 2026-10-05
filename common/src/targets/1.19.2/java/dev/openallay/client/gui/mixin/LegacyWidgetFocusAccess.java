package dev.openallay.client.gui.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Actual native focused-state mutator predating GuiEventListener.setFocused. */
@Mixin(AbstractWidget.class)
public interface LegacyWidgetFocusAccess {
    @Invoker("setFocused") void openallay$setFocused(boolean focused);
}
