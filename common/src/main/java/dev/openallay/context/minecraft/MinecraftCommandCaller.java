package dev.openallay.context.minecraft;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Resolve the actual nullable player entity without exception or permission changes. */
public final class MinecraftCommandCaller {
    private MinecraftCommandCaller() {}

    public static ServerPlayer player(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }
}
