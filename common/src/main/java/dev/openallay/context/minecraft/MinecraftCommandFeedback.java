package dev.openallay.context.minecraft;

import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** Only the native feedback signature varies; command permissions and outcomes stay shared. */
public final class MinecraftCommandFeedback {
    private MinecraftCommandFeedback() {}
    public static void success(CommandSourceStack source, Supplier<Component> message, boolean broadcast) {
        source.sendSuccess(message, broadcast);
    }
}
