package dev.openallay.world;

import dev.openallay.context.EvidenceMetadata;
import java.util.List;
import java.util.Objects;

public record BlockObservation(
        WorldBounds bounds,
        List<WorldBlockSnapshot> blocks,
        WorldObservationCoverage coverage,
        EvidenceMetadata evidence) {
    public BlockObservation {
        Objects.requireNonNull(bounds, "bounds");
        blocks = List.copyOf(blocks);
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(evidence, "evidence");
    }
}
