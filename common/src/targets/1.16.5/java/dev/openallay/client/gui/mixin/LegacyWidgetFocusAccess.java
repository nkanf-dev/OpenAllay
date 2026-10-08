package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.GuideNativeFocusAccess;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Truthful focus transitions also notify native text/drag owners when hidden or disabled. */
@Mixin(AbstractWidget.class)
public abstract class LegacyWidgetFocusAccess implements GuideNativeFocusAccess {
    @Shadow public abstract boolean isFocused();
    @Shadow private boolean focused;
    @Shadow protected abstract void onFocusedChanged(boolean focused);
    @Override public final void openallay$guideFocus(boolean focused) {
        if (isFocused() == focused) return;
        this.focused = focused;
        onFocusedChanged(focused);
    }
}
