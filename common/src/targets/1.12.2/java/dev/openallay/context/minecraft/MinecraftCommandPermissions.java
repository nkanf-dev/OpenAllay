package dev.openallay.context.minecraft;

import net.minecraft.command.ICommandSender;

/** Actual native command authority for read-only world capture. */
public final class MinecraftCommandPermissions {
    private MinecraftCommandPermissions() {}
    public static boolean canReadWorld(ICommandSender source) { return source.canUseCommand(2, "openallay"); }
}
