package dev.openallay.neoforge.command;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.CommandSuggestionHelper;
import net.minecraft.command.ISuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Invalidate the real native parse without changing the draft, cursor, or native helper. */
@Mixin(CommandSuggestionHelper.class)
abstract class ForgeCommandSuggestionsMixin implements ForgeCommandCompletionRefresh {
    @Shadow private ParseResults<ISuggestionProvider> currentParse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private boolean allowSuggestions;
    @Shadow private boolean keepSuggestions;
    @Shadow public abstract void setAllowSuggestions(boolean value);
    @Shadow public abstract void updateCommandInfo();

    @Override public void openallay$refreshCommands() {
        // Cancel before dropping custody: the native thenRun must not use a null future.
        if (pendingSuggestions != null && !pendingSuggestions.isDone()) pendingSuggestions.cancel(false);
        pendingSuggestions = null;
        currentParse = null;
        boolean allowed = allowSuggestions;
        setAllowSuggestions(false); // Real method clears inaccessible native Suggestions owner.
        setAllowSuggestions(allowed);
        keepSuggestions = false;
        updateCommandInfo();
    }
}
