package dev.openallay.client.gui.mixin;

import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Geometry-only access to the current native text field. No editor replacement or cursor mutation. */
@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {
    @Accessor("width")
    int openallay$width();

    @Mutable
    @Accessor("width")
    void openallay$width(int width);

    @Invoker("reflowDisplayLines")
    void openallay$reflowDisplayLines();
}
