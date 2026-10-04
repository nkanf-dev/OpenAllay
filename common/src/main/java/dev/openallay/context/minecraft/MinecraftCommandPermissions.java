package dev.openallay.context.minecraft;

import net.minecraft.commands.CommandSourceStack;

/** Native command authority is translated once; read-only capture policy stays shared. */
public final class MinecraftCommandPermissions {
    private MinecraftCommandPermissions() {}
    public static boolean canReadWorld(CommandSourceStack source) { return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER); }
}
