package dev.openallay.neoforge.command.mixin;

import dev.openallay.neoforge.command.ForgeCommandCompletionRefresh;

import net.minecraft.client.gui.CommandSuggestionHelper;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChatScreen.class)
abstract class ForgeChatCompletionMixin implements ForgeCommandCompletionRefresh {
    @Shadow private CommandSuggestionHelper commandSuggestions;
    @Override public void openallay$refreshCommands() {
        if (commandSuggestions != null) ((ForgeCommandCompletionRefresh) commandSuggestions).openallay$refreshCommands();
    }
}
