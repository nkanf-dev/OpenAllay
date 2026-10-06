package dev.openallay.context.minecraft;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

/** Capture sender entity itself; never substitute a same-UUID current player. */
public final class MinecraftCommandCaller {
    private MinecraftCommandCaller() {}
    public static EntityPlayerMP player(ICommandSender source) {
        return source.getCommandSenderEntity() instanceof EntityPlayerMP player ? player : null;
    }
}
