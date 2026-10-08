package dev.openallay.script.command;

import dev.openallay.model.CancellationSignal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraftforge.client.ClientCommandHandler;

/** Partial native catalog: actual client-local registry only. The server tree is not transmitted in 1.12. */
public final class MinecraftCommandCapture {
    private MinecraftCommandCapture() {}

    public static void capture(Minecraft client, CommandCapabilityRuntime runtime,
            String correlationId, Instant capturedAt) {
        if (!runtime.enabledFor(correlationId)) return;
        if (!client.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Command capability must be captured on the client thread");
        }
        net.minecraft.client.entity.EntityPlayerSP player = client.player;
        net.minecraft.client.network.NetHandlerPlayClient connection = client.getConnection();
        if (player == null || connection == null) return;
        UUID actor = player.getUniqueID();
        List<CommandCatalogSnapshot.CommandNodeSnapshot> nodes = new ArrayList<>();
        ClientCommandHandler.instance.getCommands().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
                    ICommand command = entry.getValue();
                    if (!command.checkPermission(player.getServer(), player)) return;
                    String name = entry.getKey();
                    String redirect = name.equals(command.getName()) ? "" : command.getName();
                    nodes.add(new CommandCatalogSnapshot.CommandNodeSnapshot(
                            name, name, "native_client_command_partial", "", true, redirect,
                            dev.openallay.util.Java8Collections.listOf(command.getUsage(player)), dev.openallay.util.Java8Collections.listOf()));
                });
        runtime.capture(correlationId, actor, new CommandCatalogSnapshot(capturedAt, nodes),
                (expectedActor, command, cancellation) -> submit(client, player, connection,
                        expectedActor, command, cancellation));
    }

    private static CompletableFuture<Void> submit(Minecraft client,
            net.minecraft.client.entity.EntityPlayerSP capturedPlayer,
            net.minecraft.client.network.NetHandlerPlayClient capturedConnection,
            UUID actor, String command, CancellationSignal cancellation) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        client.addScheduledTask(() -> {
            try {
                cancellation.throwIfCancelled();
                if (client.player != capturedPlayer || client.getConnection() != capturedConnection
                        || !capturedPlayer.getUniqueID().equals(actor)) {
                    throw new dev.openallay.script.JavascriptExecutionException(
                            "command_connection_unavailable", "Player command connection is unavailable");
                }
                MinecraftNativeCommandSubmission.send(client, command);
                result.complete(null);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }
}
