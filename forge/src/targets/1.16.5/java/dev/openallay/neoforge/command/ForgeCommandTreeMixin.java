package dev.openallay.neoforge.command;

import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.network.play.server.SCommandListPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetHandler.class)
abstract class ForgeCommandTreeMixin {
    @Inject(method = "handleCommands(Lnet/minecraft/network/play/server/SCommandListPacket;)V", at = @At("RETURN"), require = 1)
    private void openallay$installOwnedCommands(SCommandListPacket packet, CallbackInfo ci) {
        ForgeClientGuideCommands.treeChanged((ClientPlayNetHandler) (Object) this);
    }
}
