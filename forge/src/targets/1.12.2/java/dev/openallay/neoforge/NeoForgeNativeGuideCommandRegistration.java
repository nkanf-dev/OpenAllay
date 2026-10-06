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
        var routes = GuideCommandSpec.routes().stream().map(route ->
                new NeoForgeNativeCommandProjection.Route<>(route.literals(), route.argument(), route.action())).toList();
        NeoForgeNativeCommandRegistration.client(new NeoForgeNativeCommandProjection<>(
                GuideCommandSpec.ROOT, routes, (sender, invocation) -> GuideCommandSpec.dispatch(
                        guide, invocation.action(), invocation.value(), () -> actor(sender),
                        notice -> sender.sendMessage(new TextComponentString("[OpenAllay] " + notice.message()))),
                (server, sender) -> true, (sender, argument) -> List.of()));
    }
    private static java.util.UUID actor(ICommandSender sender) {
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayer player)) {
            throw new IllegalStateException("Guide command requires its original native player sender");
        }
        return player.getUniqueID();
    }
}
