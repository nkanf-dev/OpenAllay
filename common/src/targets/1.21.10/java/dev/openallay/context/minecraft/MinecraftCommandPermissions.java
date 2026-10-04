package dev.openallay.context.minecraft;

import net.minecraft.commands.CommandSourceStack;

/** Native command authority is translated once; read-only capture policy stays shared. */
public final class MinecraftCommandPermissions {
    private MinecraftCommandPermissions() {}
    public static boolean canReadWorld(CommandSourceStack source) { return source.hasPermission(2); }
}
