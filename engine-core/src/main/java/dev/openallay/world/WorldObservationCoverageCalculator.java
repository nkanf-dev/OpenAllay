package dev.openallay.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Exact loaded-position coverage for a cuboid without scanning every block position. */
final class WorldObservationCoverageCalculator {
    private static final int CHUNK_SIZE = 16;

    private WorldObservationCoverageCalculator() {}

    static WorldObservationCoverage calculate(
            WorldBounds bounds,
            int minimumBuildY,
            int maximumBuildY,
            BiPredicate<Integer, Integer> loadedChunk) {
        java.util.Objects.requireNonNull(bounds, "bounds");
        java.util.Objects.requireNonNull(loadedChunk, "loadedChunk");
        if (minimumBuildY > maximumBuildY) {
            throw new IllegalArgumentException("minimumBuildY must not exceed maximumBuildY");
        }

        long requested = bounds.volume();
        ArrayList<String> unavailable = new ArrayList<>();
        long firstLoadedY = Math.max((long) bounds.from().y(), minimumBuildY);
        long lastLoadedY = Math.min((long) bounds.to().y(), maximumBuildY);
        addHeightRanges(bounds, minimumBuildY, maximumBuildY, unavailable);
        if (firstLoadedY > lastLoadedY) {
            return new WorldObservationCoverage(
                    requested, 0L, false, dev.openallay.util.Java8Collections.listCopyOf(unavailable));
        }

        long loadedYCount = lastLoadedY - firstLoadedY + 1L;
        int firstChunkX = Math.floorDiv(bounds.from().x(), CHUNK_SIZE);
        int lastChunkX = Math.floorDiv(bounds.to().x(), CHUNK_SIZE);
        int firstChunkZ = Math.floorDiv(bounds.from().z(), CHUNK_SIZE);
        int lastChunkZ = Math.floorDiv(bounds.to().z(), CHUNK_SIZE);
        long loadedPositions = 0L;
        for (long chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
            long chunkMinX = chunkX * CHUNK_SIZE;
            long chunkMaxX = chunkMinX + CHUNK_SIZE - 1L;
            long width = Math.min((long) bounds.to().x(), chunkMaxX)
                    - Math.max((long) bounds.from().x(), chunkMinX)
                    + 1L;
            for (long chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                long chunkMinZ = chunkZ * CHUNK_SIZE;
                long chunkMaxZ = chunkMinZ + CHUNK_SIZE - 1L;
                long depth = Math.min((long) bounds.to().z(), chunkMaxZ)
                        - Math.max((long) bounds.from().z(), chunkMinZ)
                        + 1L;
                int x = Math.toIntExact(chunkX);
                int z = Math.toIntExact(chunkZ);
                if (loadedChunk.test(x, z)) {
                    loadedPositions = Math.addExact(
                            loadedPositions,
                            Math.multiplyExact(
                                    Math.multiplyExact(width, depth),
                                    loadedYCount));
                } else {
                    unavailable.add("chunk:" + x + "," + z);
                }
            }
        }
        return new WorldObservationCoverage(
                requested,
                loadedPositions,
                loadedPositions == requested,
                dev.openallay.util.Java8Collections.listCopyOf(unavailable));
    }

    private static void addHeightRanges(
            WorldBounds bounds,
            int minimumBuildY,
            int maximumBuildY,
            List<String> unavailable) {
        if (bounds.from().y() < minimumBuildY) {
            unavailable.add(range(
                    bounds.from().y(),
                    Math.min((long) bounds.to().y(), (long) minimumBuildY - 1L)));
        }
        if (bounds.to().y() > maximumBuildY) {
            unavailable.add(range(
                    Math.max((long) bounds.from().y(), (long) maximumBuildY + 1L),
                    bounds.to().y()));
        }
    }

    private static String range(long first, long last) {
        return "height:" + first + (first == last ? "" : ".." + last);
    }
}
