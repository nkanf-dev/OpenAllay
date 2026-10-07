package dev.openallay.model;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** One counts-only receipt per actual invocation. Place inside retry/queue decorators. */
public final class ObservingModelClient implements ModelClient {
    private final ModelClient delegate;
    private final String modelIdentifier;

    private ObservingModelClient(ModelClient delegate, String modelIdentifier) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.modelIdentifier = Objects.requireNonNull(modelIdentifier, "modelIdentifier");
    }

    public static ModelClient observe(ModelClient model) { return observe(model, ""); }

    public static ModelClient observe(ModelClient model, String modelIdentifier) {
        Objects.requireNonNull(model, "model");
        return model.observesUsage() ? model : new ObservingModelClient(model, modelIdentifier);
    }

    @Override public boolean observesUsage() { return true; }

    @Override
    public CompletableFuture<ModelTurn> complete(ModelRequest request, Consumer<ModelEvent> events,
            CancellationSignal cancellation) {
        if (cancellation.isCancelled()) return dev.openallay.util.Java8Futures.failedFuture(new ModelClientException(
                new ModelFailure("agent_cancelled", "Agent request was cancelled", null)));
        Attempt attempt = new Attempt(events);
        if (!delegate.reportsAttemptStarted()) attempt.start();
        CompletableFuture<ModelTurn> raw;
        try {
            raw = Objects.requireNonNull(delegate.complete(request, attempt::accept, cancellation), "future");
        } catch (Throwable failure) {
            attempt.seal(null);
            return dev.openallay.util.Java8Futures.failedFuture(failure);
        }
        // Seal before publishing completion to the retry loop or Agent. MessageComplete is not
        // authoritative: decoders can report it more than once or fail after reporting usage.
        CompletableFuture<ModelTurn> result = new CompletableFuture<>();
        cancellation.observe(raw).whenComplete((turn, failure) -> {
            attempt.seal(turn);
            if (failure == null) result.complete(turn);
            else result.completeExceptionally(failure);
        });
        result.whenComplete((turn, failure) -> {
            if (result.isCancelled()) raw.cancel(true);
        });
        return result;
    }

    private final class Attempt {
        private UUID id;
        private final Consumer<ModelEvent> events;
        private ModelUsage latest = ModelUsage.empty();
        private boolean sealed;

        private Attempt(Consumer<ModelEvent> events) {
            this.events = Objects.requireNonNull(events, "events");
        }

        private synchronized void start() {
            if (sealed || id != null) return;
            id = UUID.randomUUID();
            emitMetadata(new ModelEvent.UsageStarted(id, modelIdentifier));
        }

        private synchronized void accept(ModelEvent event) {
            if (sealed) return;
            if (event instanceof ModelEvent.AttemptStarted) start();
            if (event instanceof ModelEvent.UsageUpdate && ((ModelEvent.UsageUpdate) event).usage() != null) {
                latest = ((ModelEvent.UsageUpdate) event).usage();
            }
            events.accept(event);
        }

        private synchronized void seal(ModelTurn turn) {
            if (sealed) return;
            sealed = true;
            if (id == null) return; // Local preparation failed before transport dispatch.
            ModelUsage usage = turn != null && turn.usage() != null && turn.usage().reported()
                    ? turn.usage() : latest;
            String identifier = turn == null ? modelIdentifier : turn.model();
            emitMetadata(new ModelEvent.UsageObserved(id, identifier, usage));
        }

        private void emitMetadata(ModelEvent event) {
            try {
                events.accept(event);
            } catch (RuntimeException ignored) {
                // An optional billing observer must not change the actual request outcome.
            }
        }
    }
}
