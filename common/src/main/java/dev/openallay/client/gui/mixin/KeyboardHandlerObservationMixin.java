package dev.openallay.client.gui.mixin;

import dev.openallay.client.observation.ObservationMenuKeyHandler;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native handled events return before TAIL; normal menu input keeps priority. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerObservationMixin {
    @Inject(method = "keyPress", at = @At("TAIL"), require = 1)
    private void openallay$guideAfterNativeMenuKey(long handle, int action, KeyEvent event, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (handle == client.getWindow().handle()) ObservationMenuKeyHandler.afterUnhandledKey(client, action, event);
    }
}
