package dev.openallay.server;

import com.google.gson.Gson;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.context.minecraft.MinecraftContextCapture;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.world.MinecraftServerWorldObservationCoordinator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Common owning-thread server context adapter shared by Fabric and NeoForge.
 */
public final class MinecraftServerGuideContextProvider
        implements dev.openallay.bridge.server.ServerBridgeSession.ContextProvider {
    private final OpenAllayRuntime runtime;
    private final MinecraftServer server;
    private final Gson gson;

    public MinecraftServerGuideContextProvider(
            OpenAllayRuntime runtime, MinecraftServer server, Gson gson) {
        this.runtime = java.util.Objects.requireNonNull(runtime, "runtime");
        this.server = java.util.Objects.requireNonNull(server, "server");
        this.gson = java.util.Objects.requireNonNull(gson, "gson");
    }

    @Override
    public CompletableFuture<ToolInvocationContext> capture(
            UUID actorId,
            Set<ContextCapability> capabilities,
            String correlationId,
            dev.openallay.model.CancellationSignal cancellation) {
        CompletableFuture<ToolInvocationContext> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                cancellation.throwIfCancelled();
                ServerPlayer player = server.getPlayerList().getPlayer(actorId);
                if (player == null) {
                    throw new JavascriptExecutionException(
                            "world_observation_cancelled",
                            "Requesting player disconnected before server capture");
                }
                ToolInvocationContext context = new MinecraftContextCapture(
                                gson, runtime.platform())
                        .capture(player.createCommandSourceStack(), capabilities, correlationId);
                cancellation.throwIfCancelled();
                context.player().ifPresent(snapshot -> runtime.worldObservations().capture(
                        correlationId,
                        new MinecraftServerWorldObservationCoordinator(
                                server,
                                runtime.platform(),
                                snapshot.uuid(),
                                snapshot.dimension())));
                result.complete(context);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }
}
