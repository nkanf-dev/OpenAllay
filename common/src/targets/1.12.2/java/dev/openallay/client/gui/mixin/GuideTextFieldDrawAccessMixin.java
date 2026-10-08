package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.GuideTextFieldDrawAccess;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Actual GuiTextField private text member, proven by the successful native-source census. */
@Mixin(GuiTextField.class)
public abstract class GuideTextFieldDrawAccessMixin implements GuideTextFieldDrawAccess {
    @Shadow private String text;
    @Override public final String openallay$drawText() { return text; }
    @Override public final void openallay$drawText(String text) { this.text = text; }
}
