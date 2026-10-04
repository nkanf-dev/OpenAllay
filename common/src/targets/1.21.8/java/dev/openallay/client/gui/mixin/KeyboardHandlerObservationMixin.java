package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.GuideNativeInput;
import dev.openallay.client.observation.ObservationMenuKeyHandler;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native handled events return before TAIL; normal menu input keeps priority. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerObservationMixin {
    @Inject(method = "keyPress(JIIII)V", at = @At("TAIL"), require = 1)
    private void openallay$guideAfterNativeMenuKey(long handle, int key, int scancode, int action, int modifiers, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (handle == client.getWindow().getWindow()) ObservationMenuKeyHandler.afterUnhandledKey(client, action,
                GuideNativeInput.capture(key, scancode, modifiers));
    }
}
