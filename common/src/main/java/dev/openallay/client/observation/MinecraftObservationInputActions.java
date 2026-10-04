package dev.openallay.client.observation;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.context.ClientFocusCapture;
import dev.openallay.model.CancellationSignal;
import dev.openallay.platform.PlatformService;
import dev.openallay.world.ClientObservationAnchor;
import dev.openallay.world.MinecraftClientWorldObservationCoordinator;
import dev.openallay.world.WorldObservationRuntime;
import dev.openallay.world.WorldViewRequest;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

/** Native source adapter for the supporting input UI; Agent requests use their own world runtime. */
public final class MinecraftObservationInputActions implements GuideObservationInputActions, AutoCloseable {
    private final Minecraft client;
    private final PlatformService platform;
    private final WorldObservationRuntime observations;
    private final ConcurrentHashMap<String, String> producers = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public MinecraftObservationInputActions(Minecraft client, PlatformService platform,
            WorldObservationRuntime observations) {
        this.client = Objects.requireNonNull(client, "client");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.observations = Objects.requireNonNull(observations, "observations");
    }

    @Override public ClientObservationAnchor captureFocus() {
        requireActive();
        return ClientObservationAnchor.focus(ClientFocusCapture.capture(client, platform));
    }

    @Override public CompletableFuture<ClientObservationAnchor> captureCurrentFrame() {
        try {
            requireActive();
            var focus = ClientFocusCapture.capture(client, platform);
            String correlation = "input-view:" + UUID.randomUUID();
            var coordinator = new MinecraftClientWorldObservationCoordinator(client, platform,
                    focus.actorId(), focus.dimension(), observations, correlation);
            observations.capture(correlation, coordinator);
            CancellationSignal cancellation = new CancellationSignal();
            var target = MinecraftClientViewCapture.owns(MinecraftClientWindow.screen(client))
                    ? WorldViewRequest.Target.WORLD : WorldViewRequest.Target.GAME_UI;
            return coordinator.capture(new WorldViewRequest(target), cancellation).toCompletableFuture()
                    .thenCompose(view -> {
                        CompletableFuture<ClientObservationAnchor> result = new CompletableFuture<>();
                        client.execute(() -> {
                            if (closed || client.player == null || client.level == null
                                    || !focus.actorId().equals(client.player.getUUID())
                                    || !focus.dimension().equals(client.level.dimension().identifier().toString())) {
                                observations.releaseImageProducers(correlation);
                                result.completeExceptionally(new IllegalStateException("Input view source changed"));
                                return;
                            }
                            ClientObservationAnchor anchor = new ClientObservationAnchor(UUID.randomUUID(),
                                    focus.capturedAt(), focus, java.util.Optional.of(view));
                            producers.put(anchor.associationId().toString(), correlation);
                            observations.closeObservations(correlation);
                            result.complete(anchor);
                        });
                        return result;
                    }).whenComplete((anchor, failure) -> {
                        if (failure != null) observations.releaseImageProducers(correlation);
                    });
        } catch (RuntimeException unavailable) {
            return CompletableFuture.failedFuture(unavailable);
        }
    }

    @Override public CompletableFuture<Void> releaseCapture(ClientObservationAnchor anchor) {
        String correlation = producers.remove(anchor.associationId().toString());
        return correlation == null ? CompletableFuture.completedFuture(null)
                : observations.releaseImageProducers(correlation);
    }

    public void clearConnectionState() {
        for (String association : java.util.List.copyOf(producers.keySet())) {
            String correlation = producers.remove(association);
            if (correlation != null) observations.releaseImageProducers(correlation);
        }
    }

    @Override public void close() { closed = true; clearConnectionState(); }

    private void requireActive() {
        if (closed || client.player == null || client.level == null) {
            throw new IllegalStateException("No native game view is available");
        }
        if (!client.isSameThread()) throw new IllegalStateException("Input observation requires the client thread");
    }
}
