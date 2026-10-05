package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.MinecraftTeardownState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact old native disconnect scopes; the pre-1.20.3 client has no native teardown field. */
@Mixin(Minecraft.class)
public abstract class MinecraftTeardownStateMixin implements MinecraftTeardownState {
    @Unique private int openallay$teardownDepth;
    public final boolean openallay$teardownInProgress() { return openallay$teardownDepth > 0; }

    @Inject(method = {"clearLevel()V", "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V"}, at = @At("HEAD"))
    private void openallay$beginTeardown(CallbackInfo callback) { openallay$teardownDepth++; }
    @Inject(method = {"clearLevel()V", "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V"}, at = @At("RETURN"))
    private void openallay$endTeardown(CallbackInfo callback) { openallay$teardownDepth--; }
}
