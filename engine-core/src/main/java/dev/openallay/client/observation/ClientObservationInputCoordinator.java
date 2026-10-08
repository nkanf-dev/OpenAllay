package dev.openallay.client.observation;

import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.client.gui.GuideClientUiState;
import dev.openallay.world.ClientObservationAnchor;
import dev.openallay.tool.ToolResult;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Keeps source capture and draft mutation on the client dispatcher. No screen swapping or image import. */
public final class ClientObservationInputCoordinator {
    private final GuideObservationInputActions input;
    private final ClientEventDispatcher client;

    public ClientObservationInputCoordinator(GuideObservationInputActions input, ClientEventDispatcher client) {
        this.input = Objects.requireNonNull(input, "input");
        this.client = Objects.requireNonNull(client, "client");
    }

    public void seedFocus(GuideClientUiState state, String session) {
        if (!state.observationInitialized(session)) state.seedObservation(session, input.captureFocus());
    }

    /** Captures before opening Guide. An explicit removal is not silently undone on entry. */
    public CompletableFuture<Boolean> beforeGuide(GuideClientUiState state, String session,
            BooleanSupplier stillCurrent, Runnable open) {
        GuideClientUiState.ObservationCapture captured = state.captureObservation(session);
        return complete(input.captureBeforeGuide(), state, captured, stillCurrent, open, true);
    }

    public boolean refresh(GuideClientUiState state, String session) {
        return state.replaceObservation(state.captureObservation(session), input.captureFocus());
    }

    /** A frame attachment creates a new actual source association. It never relabels an old image. */
    public CompletableFuture<Boolean> attachCurrentFrame(GuideClientUiState state, String session) {
        GuideClientUiState.ObservationCapture captured = state.captureObservation(session);
        return complete(input.captureCurrentFrame(), state, captured, () -> true, () -> {}, false);
    }

    private CompletableFuture<Boolean> complete(CompletableFuture<ClientObservationAnchor> source,
            GuideClientUiState state, GuideClientUiState.ObservationCapture captured,
            BooleanSupplier stillCurrent, Runnable after, boolean seed) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        source.whenComplete((anchor, failure) -> client.execute(() -> {
            if (failure != null) { result.completeExceptionally(failure); return; }
            if (state.closed() || !stillCurrent.getAsBoolean()) {
                release(anchor, result, false, () -> {});
                return;
            }
            try {
                boolean unchanged = state.captureObservation(captured.session()).equals(captured);
                boolean applied = seed ? unchanged && state.seedObservation(captured.session(), anchor)
                        : state.replaceObservation(captured, anchor);
                if (!applied) {
                    release(anchor, result, false, () -> { if (!state.closed() && stillCurrent.getAsBoolean()) after.run(); });
                    return;
                }
                GuideClientUiState.ObservationCapture appliedCapture = state.captureObservation(captured.session());
                CompletableFuture<ToolResult<Boolean>> custody = dev.openallay.util.Java8ApiSupport.isEmpty(anchor.image())
                        ? CompletableFuture.completedFuture(new ToolResult.Success<>(true)) : state.observationImagesSettled();
                custody.whenComplete((ack, pinFailure) -> client.execute(() -> {
                    final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Success<Boolean> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (pinFailure != null || !((($oaPattern0_holder.value = ack) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<Boolean>) $oaPattern0_holder.value) != null))) || !Boolean.TRUE.equals($oaPattern0_holder.bound.value())) {
                        // Keep the temporary producer until connection disposal. Do not expose unpinned pixels as ready.
                        state.replaceObservation(appliedCapture, new ClientObservationAnchor(anchor.associationId(),
                                anchor.capturedAt(), anchor.focus(), Optional.empty()));
                        result.completeExceptionally(pinFailure != null ? pinFailure
                                : new IllegalStateException("Observation draft image custody was not acknowledged"));
                        return;
                    }
                    boolean available = !state.closed() && stillCurrent.getAsBoolean();
                    release(anchor, result, available, () -> { if (!state.closed() && stillCurrent.getAsBoolean()) after.run(); });
                }));
            } catch (RuntimeException failed) { result.completeExceptionally(failed); }
        }));
        return result;
    }

    private void release(ClientObservationAnchor anchor, CompletableFuture<Boolean> result,
            boolean applied, Runnable after) {
        try {
            input.releaseCapture(anchor).whenComplete((ignored, failure) -> client.execute(() -> {
                if (failure != null) { result.completeExceptionally(failure); return; }
                try { after.run(); result.complete(applied); }
                catch (RuntimeException failed) { result.completeExceptionally(failed); }
            }));
        } catch (RuntimeException failed) { result.completeExceptionally(failed); }
    }
}
