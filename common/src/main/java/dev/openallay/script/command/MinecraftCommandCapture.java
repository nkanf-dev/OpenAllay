package dev.openallay.script.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.openallay.model.CancellationSignal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/** Minecraft-thread adapter for the current player's complete active client command tree. */
public final class MinecraftCommandCapture {
    private MinecraftCommandCapture() {}

    public static void capture(
            Minecraft client,
            CommandCapabilityRuntime runtime,
            String correlationId,
            Instant capturedAt) {
        if (!runtime.enabledFor(correlationId)) {
            return;
        }
        if (!client.isSameThread()) {
            throw new IllegalStateException("Command capability must be captured on the client thread");
        }
        if (client.player == null || client.getConnection() == null) {
            return;
        }
        UUID actorId = client.player.getUUID();
        var connection = client.getConnection();
        var dispatcher = connection.getCommands();
        ClientSuggestionProvider source = connection.getSuggestionsProvider();
        List<CommandCatalogSnapshot.CommandNodeSnapshot> nodes = new ArrayList<>();
        flatten(dispatcher, source, dispatcher.getRoot(), "", nodes);
        CommandCatalogSnapshot catalog = new CommandCatalogSnapshot(capturedAt, nodes);
        runtime.capture(
                correlationId,
                actorId,
                catalog,
                (expectedActor, command, cancellation) ->
                        submit(client, expectedActor, command, cancellation));
    }

    private static CompletableFuture<Void> submit(
            Minecraft client,
            UUID expectedActor,
            String command,
            CancellationSignal cancellation) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        client.execute(() -> {
            try {
                cancellation.throwIfCancelled();
                if (client.player == null
                        || client.getConnection() == null
                        || !client.player.getUUID().equals(expectedActor)) {
                    throw new dev.openallay.script.JavascriptExecutionException(
                            "command_connection_unavailable",
                            "Player command connection is unavailable");
                }
                client.getConnection().sendCommand(command);
                result.complete(null);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private static <S> void flatten(
            CommandDispatcher<S> dispatcher,
            S source,
            CommandNode<S> parent,
            String parentPath,
            List<CommandCatalogSnapshot.CommandNodeSnapshot> target) {
        for (CommandNode<S> child : parent.getChildren()) {
            String segment = segment(child);
            String path = parentPath.isEmpty() ? segment : parentPath + " " + segment;
            Map<CommandNode<S>, String> usage =
                    dispatcher.getSmartUsage(child, source);
            List<String> usages = usage.values().stream().sorted().toList();
            List<String> children = child.getChildren().stream()
                    .map(MinecraftCommandCapture::segment)
                    .toList();
            String redirect = child.getRedirect() == null
                    ? ""
                    : pathOf(dispatcher.getRoot(), child.getRedirect(), "");
            target.add(new CommandCatalogSnapshot.CommandNodeSnapshot(
                    path,
                    child.getName(),
                    child instanceof LiteralCommandNode<?> ? "literal" : "argument",
                    argumentType(child),
                    child.getCommand() != null,
                    redirect,
                    usages,
                    children));
            flatten(dispatcher, source, child, path, target);
        }
    }

    private static String segment(CommandNode<?> node) {
        return node instanceof ArgumentCommandNode<?, ?>
                ? "<" + node.getName() + ">"
                : node.getName();
    }

    private static String argumentType(CommandNode<?> node) {
        if (!(node instanceof ArgumentCommandNode<?, ?> argument)) {
            return "";
        }
        ArgumentType<?> type = argument.getType();
        return type.getClass().getName();
    }

    private static String pathOf(
            CommandNode<?> current, CommandNode<?> sought, String parentPath) {
        for (CommandNode<?> child : current.getChildren()) {
            String path = parentPath.isEmpty()
                    ? segment(child)
                    : parentPath + " " + segment(child);
            if (child == sought) {
                return path;
            }
            String nested = pathOf(child, sought, path);
            if (!nested.isEmpty()) {
                return nested;
            }
        }
        return "";
    }
}
