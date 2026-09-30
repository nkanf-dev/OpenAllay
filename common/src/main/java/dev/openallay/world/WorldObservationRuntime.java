package dev.openallay.world;

import dev.openallay.model.CancellationSignal;
import dev.openallay.context.EvidenceMetadata;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Owns request-scoped world coordinators and drops them on every terminal path. */
public final class WorldObservationRuntime {
    private final ConcurrentMap<String, WorldObservationCoordinator> requests =
            new ConcurrentHashMap<>();

    public void capture(String correlationId, WorldObservationCoordinator coordinator) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        WorldObservationCoordinator candidate =
                java.util.Objects.requireNonNull(coordinator, "coordinator");
        WorldObservationCoordinator existing = requests.putIfAbsent(correlationId, candidate);
        if (existing != null && existing != candidate) {
            candidate.close();
        }
    }

    public Optional<JavascriptWorldBridge> bridge(
            String correlationId, CancellationSignal cancellation) {
        return bridge(correlationId, cancellation, ignored -> {});
    }

    public Optional<JavascriptWorldBridge> bridge(
            String correlationId,
            CancellationSignal cancellation,
            Consumer<EvidenceMetadata> evidence) {
        WorldObservationCoordinator coordinator = requests.get(correlationId);
        return coordinator == null
                ? Optional.empty()
                : Optional.of(new JavascriptWorldBridge(coordinator, cancellation, evidence));
    }

    public void closeRequest(String correlationId) {
        WorldObservationCoordinator coordinator = requests.remove(correlationId);
        if (coordinator != null) {
            coordinator.close();
        }
    }
}
