package dev.openallay.world;

import dev.openallay.model.CancellationSignal;
import java.util.concurrent.CompletionStage;

/**
 * Request-scoped bridge that schedules owning-thread capture and publishes detached values only.
 */
public interface WorldObservationCoordinator extends AutoCloseable {
    CompletionStage<BlockObservation> inspect(
            WorldObservationRequest request, CancellationSignal cancellation);

    CompletionStage<EntityObservation> entities(
            WorldObservationRequest request, CancellationSignal cancellation);

    CompletionStage<WorldEntitySnapshot> entity(
            String observationId, CancellationSignal cancellation);

    default CompletionStage<WorldFocusObservation> focus(CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        return dev.openallay.util.Java8Futures.failedFuture(new dev.openallay.script.JavascriptExecutionException(
                        "client_observation_unavailable", "Current focus requires the player's connected client"));
    }

    default CompletionStage<WorldViewCapture> capture(
            WorldViewRequest request, CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        return dev.openallay.util.Java8Futures.failedFuture(new dev.openallay.script.JavascriptExecutionException(
                        "client_observation_unavailable", "Native view capture requires the player's connected client"));
    }

    @Override
    default void close() {}
}
