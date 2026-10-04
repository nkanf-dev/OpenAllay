package dev.openallay.world;

import java.util.List;

public record WorldObservationCoverage(
        long requestedPositions,
        long loadedPositions,
        boolean complete,
        List<String> unavailableSections) {
    public WorldObservationCoverage {
        if (requestedPositions < 0 || loadedPositions < 0 || loadedPositions > requestedPositions) {
            throw new IllegalArgumentException("Invalid world observation coverage");
        }
        unavailableSections = List.copyOf(unavailableSections);
    }
}
