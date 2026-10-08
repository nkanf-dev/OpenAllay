package dev.openallay.neoforge;

import dev.openallay.command.GuideCommandSpec;
import dev.openallay.guide.GuideCommandFacade;
import dev.openallay.guide.GuideNotice;
import java.util.List;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;

/** Native sender owns actor and feedback; no replacement-player lookup supplies command custody. */
final class NeoForgeNativeGuideCommandRegistration {
    private NeoForgeNativeGuideCommandRegistration() {}
    static void register(GuideCommandFacade guide) {
        java.util.List<dev.openallay.neoforge.NeoForgeNativeCommandProjection.Route<dev.openallay.command.GuideCommandSpec.Action>> routes = dev.openallay.util.Java8Collections.toList(GuideCommandSpec.routes().stream().map(route ->
                new NeoForgeNativeCommandProjection.Route<>(route.literals(), route.argument(), route.action())));
        NeoForgeNativeCommandRegistration.client(new NeoForgeNativeCommandProjection<>(
                GuideCommandSpec.ROOT, routes, (sender, invocation) -> GuideCommandSpec.dispatch(
                        guide, invocation.action(), invocation.value(), () -> actor(sender),
                        notice -> sender.sendMessage(new TextComponentString("[OpenAllay] " + notice.message()))),
                (server, sender) -> true, (sender, argument) -> dev.openallay.util.Java8Collections.listOf()));
    }
    private static java.util.UUID actor(ICommandSender sender) {
        final class $oaPattern0_Holder { net.minecraft.entity.Entity value; EntityPlayer bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (!((($oaPattern0_holder.value = sender.getCommandSenderEntity()) instanceof net.minecraft.entity.player.EntityPlayer && (($oaPattern0_holder.bound = (EntityPlayer) $oaPattern0_holder.value) != null)))) {
            throw new IllegalStateException("Guide command requires its original native player sender");
        }
        return $oaPattern0_holder.bound.getUniqueID();
    }
}
