package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.GuideNativeScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Older native Screen lacks tooltip clearing. Owned paint scopes draw their selected tooltip once. */
@Mixin(Screen.class)
public abstract class ScreenTooltipScopeMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"), cancellable = true)
    private void openallay$ownedTooltipScope(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if ((Object) this instanceof GuideNativeScreen owned) {
            owned.render(graphics, mouseX, mouseY, delta);
            callback.cancel();
        }
    }
}
