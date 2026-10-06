package dev.openallay.client.gui.mixin;

import dev.openallay.client.lifecycle.MinecraftClientShutdownCallbacks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Orderly client shutdown before Minecraft releases its render resources. */
@Mixin(Minecraft.class)
public abstract class MinecraftClientShutdownMixin {
    @Inject(method = "close()V", at = @At("HEAD"))
    private void openallay$onClientClose(CallbackInfo callback) {
        MinecraftClientShutdownCallbacks.onClose();
    }
}
