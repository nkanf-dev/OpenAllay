package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.GuideComposerGeometry;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact Minecraft 1.21 access. Keep the native editor and its live IME state during layout changes. */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxAccessor extends AbstractTextAreaWidget
        implements GuideComposerGeometry.NativeAccess {
    /** Mixin superclass signature only; product code never creates this abstract mixin. */
    protected MultiLineEditBoxAccessor(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Override
    @Accessor("textField")
    public abstract MultilineTextField openallay$textField();

    @Override
    @Unique
    public int openallay$totalInnerPadding() {
        return totalInnerPadding();
    }

    @Override
    @Unique
    public void openallay$refreshScrollAmount() {
        refreshScrollAmount();
    }
}
