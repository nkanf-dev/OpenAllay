package dev.openallay.world;

import dev.openallay.context.EvidenceMetadata;
import java.util.List;
import java.util.Objects;

public record EntityObservation(
        WorldBounds bounds,
        List<WorldEntitySummary> entities,
        WorldObservationCoverage coverage,
        EvidenceMetadata evidence) {
    public EntityObservation {
        Objects.requireNonNull(bounds, "bounds");
        entities = List.copyOf(entities);
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(evidence, "evidence");
    }
}
