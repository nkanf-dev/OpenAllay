package dev.openallay.context.minecraft;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

/** Capture sender entity itself; never substitute a same-UUID current player. */
public final class MinecraftCommandCaller {
    private MinecraftCommandCaller() {}
    public static ICommandSender source(EntityPlayerMP player) { return player; }
    public static EntityPlayerMP player(ICommandSender source) {
        final class $oaPattern0_Holder { net.minecraft.entity.Entity value; EntityPlayerMP bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
return (($oaPattern0_holder.value = source.getCommandSenderEntity()) instanceof net.minecraft.entity.player.EntityPlayerMP && (($oaPattern0_holder.bound = (EntityPlayerMP) $oaPattern0_holder.value) != null)) ? $oaPattern0_holder.bound : null;
    }
}
