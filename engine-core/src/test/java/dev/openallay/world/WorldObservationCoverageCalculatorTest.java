package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class WorldObservationCoverageCalculatorTest {
    @Test
    void countsBuildHeightAndChunkIntersectionsWithoutPerBlockScanning() {
        WorldBounds bounds = new WorldBounds(
                new WorldPosition(14, -2, 0),
                new WorldPosition(17, 3, 1));

        WorldObservationCoverage coverage =
                WorldObservationCoverageCalculator.calculate(
                        bounds,
                        0,
                        2,
                        (chunkX, chunkZ) -> chunkX == 0 && chunkZ == 0);

        assertEquals(48L, coverage.requestedPositions());
        assertEquals(12L, coverage.loadedPositions());
        assertFalse(coverage.complete());
        assertEquals(
                List.of("height:-2..-1", "height:3", "chunk:1,0"),
                coverage.unavailableSections());
    }

    @Test
    void reportsCompleteOnlyWhenEveryRequestedPositionIsLoaded() {
        WorldBounds bounds = new WorldBounds(
                new WorldPosition(-1, 0, -1),
                new WorldPosition(1, 2, 1));

        WorldObservationCoverage coverage =
                WorldObservationCoverageCalculator.calculate(
                        bounds, 0, 2, (chunkX, chunkZ) -> true);

        assertEquals(27L, coverage.loadedPositions());
        assertTrue(coverage.complete());
        assertEquals(List.of(), coverage.unavailableSections());
    }

    @Test
    void doesNotQueryChunksWhenTheWholeVerticalRangeIsUnavailable() {
        AtomicInteger chunkQueries = new AtomicInteger();
        WorldBounds bounds = new WorldBounds(
                new WorldPosition(0, -8, 0),
                new WorldPosition(31, -1, 31));

        WorldObservationCoverage coverage =
                WorldObservationCoverageCalculator.calculate(
                        bounds,
                        0,
                        255,
                        (chunkX, chunkZ) -> {
                            chunkQueries.incrementAndGet();
                            return true;
                        });

        assertEquals(0, chunkQueries.get());
        assertEquals(0L, coverage.loadedPositions());
        assertFalse(coverage.complete());
        assertEquals(List.of("height:-8..-1"), coverage.unavailableSections());
    }
}
