package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class WorldBoundsTest {
    @Test
    void normalizesInclusiveCoordinatesWithoutAnArbitraryVolumeCap() {
        WorldBounds bounds = new WorldBounds(
                new WorldPosition(9, 7, 5),
                new WorldPosition(-2, 7, 1));

        assertEquals(new WorldPosition(-2, 7, 1), bounds.from());
        assertEquals(new WorldPosition(9, 7, 5), bounds.to());
        assertEquals(60, bounds.volume());
    }
}
