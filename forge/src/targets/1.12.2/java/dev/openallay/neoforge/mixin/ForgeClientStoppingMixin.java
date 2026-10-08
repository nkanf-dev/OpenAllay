package dev.openallay.neoforge.mixin;

import dev.openallay.neoforge.NeoForgeNativeClientLifecycle;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FML14 lacks a game-stopping event; run before native GL/window destruction. */
@Mixin(Minecraft.class)
abstract class ForgeClientStoppingMixin {
    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void openallay$stopping(CallbackInfo callback) { NeoForgeNativeClientLifecycle.stopping(); }
}
