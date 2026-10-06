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
    private final java.util.function.Function<UUID, dev.openallay.bridge.server.ServerBridgeSession.Transport> binder;
    private final ServerPlayer expectedPlayer;
    private final net.minecraft.server.level.ServerLevel expectedLevel;
    private final java.util.function.BooleanSupplier connectionCurrent;
    private final dev.openallay.bridge.server.ServerBridgeSession.Transport bound;

    public MinecraftServerGuideContextProvider(OpenAllayRuntime runtime, MinecraftServer server, Gson gson,
            java.util.function.Function<UUID, dev.openallay.bridge.server.ServerBridgeSession.Transport> binder) {
        this(runtime, server, gson, binder, null, null, null, null);
    }
    private MinecraftServerGuideContextProvider(OpenAllayRuntime runtime, MinecraftServer server, Gson gson,
            java.util.function.Function<UUID, dev.openallay.bridge.server.ServerBridgeSession.Transport> binder,
            ServerPlayer player, net.minecraft.server.level.ServerLevel level,
            java.util.function.BooleanSupplier connection, dev.openallay.bridge.server.ServerBridgeSession.Transport bound) {
        this.runtime = runtime; this.server = server; this.gson = gson; this.binder = binder;
        this.expectedPlayer = player; this.expectedLevel = level; this.connectionCurrent = connection; this.bound = bound;
    }
    @Override public dev.openallay.bridge.server.ServerBridgeSession.ContextProvider bind(UUID actor) {
        if (bound != null) {
            if (!expectedPlayer.getUUID().equals(actor)) throw new IllegalArgumentException("Bound context actor differs");
            return this;
        }
        if (!server.isSameThread()) throw new IllegalStateException("Context bind requires server owner");
        ServerPlayer player = java.util.Objects.requireNonNull(server.getPlayerList().getPlayer(actor), "Actor disconnected");
        return new MinecraftServerGuideContextProvider(runtime, server, gson, binder, player,
                dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player),
                NativeServerConnectionGuard.capture(player), binder.apply(actor));
    }

    @Override
    public CompletableFuture<ToolInvocationContext> capture(
            UUID actorId,
            Set<ContextCapability> capabilities,
            String correlationId,
            dev.openallay.model.CancellationSignal cancellation) {
        CompletableFuture<ToolInvocationContext> result = new CompletableFuture<>();
        if (bound == null) return bind(actorId).capture(actorId, capabilities, correlationId, cancellation);
        bound.dispatch(actorId, () -> {
            try {
                cancellation.throwIfCancelled();
                ServerPlayer player = server.getPlayerList().getPlayer(actorId);
                if (player != expectedPlayer || !connectionCurrent.getAsBoolean()
                        || dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player) != expectedLevel) {
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
                                snapshot.dimension(), expectedPlayer, expectedLevel, connectionCurrent,
                                (action, retired) -> bound.dispatchLater(actorId, action, retired))));
                result.complete(context);
            } catch (RuntimeException failure) { result.completeExceptionally(failure); }
            catch (Error failure) { result.completeExceptionally(failure); throw failure; }
        }, () -> {
            if (!result.isDone()) result.completeExceptionally(new JavascriptExecutionException(
                    "world_observation_cancelled", "Original player admission retired before server capture"));
        });
        return result;
    }
}
