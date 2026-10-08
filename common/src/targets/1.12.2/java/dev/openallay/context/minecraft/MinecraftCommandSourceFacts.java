package dev.openallay.context.minecraft;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/** FML14 keeps the original ICommandSender as native command authority. */
public final class MinecraftCommandSourceFacts {
    private MinecraftCommandSourceFacts() {}
    public static MinecraftServer server(ICommandSender source) {
        return java.util.Objects.requireNonNull(source.getServer(), "Command sender has no server");
    }
    public static World level(ICommandSender source) { return source.getEntityWorld(); }
    public static String name(ICommandSender source) { return source.getName(); }
}
