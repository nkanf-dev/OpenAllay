package dev.openallay.context.minecraft;

import java.util.function.Supplier;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.ITextComponent;

/** Native feedback supplies the original sender, including native operator broadcast. */
public final class MinecraftCommandFeedback {
    private MinecraftCommandFeedback() {}
    public static void success(ICommandSender source, Supplier<ITextComponent> message, boolean broadcast) {
        ITextComponent value = message.get();
        if (broadcast) net.minecraft.command.CommandBase.notifyCommandListener(source,
                source.getServer().getCommandManager().getCommands().get("openallay"), 0,
                "%s", value.getUnformattedText());
        else source.sendMessage(value);
    }
}
