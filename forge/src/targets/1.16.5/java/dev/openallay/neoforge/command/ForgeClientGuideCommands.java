package dev.openallay.neoforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.openallay.guide.GuideCommandFacade;
import dev.openallay.neoforge.NeoForgeGuideCommands;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.command.ISuggestionProvider;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

/** One local execution owner, with the same grammar published into native chat completion. */
public final class ForgeClientGuideCommands {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static GuideCommandFacade guide;
    private static ClientPlayNetHandler connection;
    private static CommandDispatcher<ForgeClientCommandSource> execution;
    private static CommandDispatcher<ISuggestionProvider> completion;
    private static LiteralCommandNode<ISuggestionProvider> ownedRoot;
    private static ForgeClientCommandSource source;
    private ForgeClientGuideCommands() {}

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        MinecraftForge.EVENT_BUS.addListener((ClientChatEvent event) -> {
            if (submit(event.getMessage(), true)) event.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggedInEvent event) ->
                treeChanged(Minecraft.getInstance().getConnection()));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggedOutEvent event) -> clear());
    }
    public static void register(GuideCommandFacade value) {
        guide = java.util.Objects.requireNonNull(value, "guide");
        treeChanged(Minecraft.getInstance().getConnection());
    }
    public static void treeChanged(ClientPlayNetHandler next) {
        if (next == null || guide == null) return;
        var dispatcher = next.getCommands();
        if (connection == next && completion == dispatcher && ownedRoot != null
                && dispatcher.getRoot().getChild("guide") == ownedRoot) return;
        // Local exact root owns /guide. A foreign server root is never merged or deleted.
        // Refuse publication on collision and leave both completion and submission server-owned.
        var foreign = dispatcher.getRoot().getChild("guide");
        if (foreign != null && foreign != ownedRoot) {
            clearConnection();
            refreshCompletion();
            return;
        }
        connection = next;
        completion = dispatcher;
        source = new ForgeClientCommandSource(next);
        execution = new CommandDispatcher<>();
        execution.register(NeoForgeGuideCommands.tree(guide, value -> value));
        ownedRoot = dispatcher.register(NeoForgeGuideCommands.tree(guide, ignored -> source));
        refreshCompletion();
    }
    private static void refreshCompletion() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.getConnection() != null
                && client.screen instanceof ForgeCommandCompletionRefresh refresh) refresh.openallay$refreshCommands();
    }
    public static boolean submit(String message, boolean recentChat) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) throw new IllegalStateException("Local commands require the client thread");
        if (connection != client.getConnection() || execution == null || source == null || ownedRoot == null
                || completion.getRoot().getChild("guide") != ownedRoot) return false;
        if (!message.startsWith("/")) return false;
        String command = message.substring(1);
        int end = 0;
        while (end < command.length() && !Character.isWhitespace(command.charAt(end))) end++;
        if (!command.substring(0, end).equals("guide")) return false;
        if (recentChat) client.gui.getChat().addRecentChat(message);
        try { execution.execute(command, source); }
        catch (CommandSyntaxException failure) { source.feedback(failure.getMessage()); }
        return true;
    }
    private static void clearConnection() {
        connection = null; completion = null; ownedRoot = null; execution = null; source = null;
    }
    public static void clear() { clearConnection(); }
    public static void shutdown() { clearConnection(); guide = null; }
}
