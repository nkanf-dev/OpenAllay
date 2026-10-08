package dev.openallay.context.minecraft;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Only actual native command source facts; caller permissions and capture policy stay shared. */
public final class MinecraftCommandSourceFacts {
    private MinecraftCommandSourceFacts() {}
    public static MinecraftServer server(CommandSourceStack source) { return source.getServer(); }
    public static ServerLevel level(CommandSourceStack source) { return source.getLevel(); }
    public static String name(CommandSourceStack source) { return source.getTextName(); }
}
