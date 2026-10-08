package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.MinecraftTeardownState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual native unload and shutdown scopes; no guessed modern clearLevel callback. */
@Mixin(Minecraft.class)
public abstract class MinecraftTeardownStateMixin implements MinecraftTeardownState {
    @Unique private int openallay$teardownDepth;
    @Unique private boolean openallay$shutdown;
    @Override public final boolean openallay$teardownInProgress() {
        return openallay$shutdown || openallay$teardownDepth > 0;
    }
    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void openallay$beginWorldChange(CallbackInfo callback) { openallay$teardownDepth++; }
    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("RETURN"))
    private void openallay$endWorldChange(CallbackInfo callback) { openallay$teardownDepth--; }
    @Inject(method = "shutdown()V", at = @At("HEAD"))
    private void openallay$beginShutdown(CallbackInfo callback) { openallay$shutdown = true; }
}
