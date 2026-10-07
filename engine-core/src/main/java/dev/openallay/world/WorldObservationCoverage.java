package dev.openallay.world;

import java.util.List;

@dev.openallay.value.ValueType(WorldObservationCoverage.ValueSchemaProvider.class)
public final class WorldObservationCoverage {
    private final long requestedPositions;
    private final long loadedPositions;
    private final boolean complete;
    private final List<String> unavailableSections;
    public WorldObservationCoverage(long requestedPositions, long loadedPositions, boolean complete, List<String> unavailableSections) {

        if (requestedPositions < 0 || loadedPositions < 0 || loadedPositions > requestedPositions) {
            throw new IllegalArgumentException("Invalid world observation coverage");
        }
        unavailableSections = dev.openallay.util.Java8Collections.listCopyOf(unavailableSections);

        this.requestedPositions = requestedPositions;
        this.loadedPositions = loadedPositions;
        this.complete = complete;
        this.unavailableSections = unavailableSections;
    }
    public long requestedPositions() { return requestedPositions; }
    public long loadedPositions() { return loadedPositions; }
    public boolean complete() { return complete; }
    public List<String> unavailableSections() { return unavailableSections; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldObservationCoverage)) return false;
        WorldObservationCoverage that = (WorldObservationCoverage) other;
        return requestedPositions == that.requestedPositions && loadedPositions == that.loadedPositions && complete == that.complete && java.util.Objects.equals(unavailableSections, that.unavailableSections);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(requestedPositions);
        hash = 31 * hash + Long.hashCode(loadedPositions);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + java.util.Objects.hashCode(unavailableSections);
        return hash;
    }
    @Override public String toString() { return "WorldObservationCoverage[requestedPositions=" + requestedPositions + ", loadedPositions=" + loadedPositions + ", complete=" + complete + ", unavailableSections=" + unavailableSections + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldObservationCoverage> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldObservationCoverage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldObservationCoverage>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldObservationCoverage.class, "requestedPositions", WorldObservationCoverage::requestedPositions), new dev.openallay.value.ValueSchema.Component<>(WorldObservationCoverage.class, "loadedPositions", WorldObservationCoverage::loadedPositions), new dev.openallay.value.ValueSchema.Component<>(WorldObservationCoverage.class, "complete", WorldObservationCoverage::complete), new dev.openallay.value.ValueSchema.Component<>(WorldObservationCoverage.class, "unavailableSections", WorldObservationCoverage::unavailableSections)), arguments -> new WorldObservationCoverage((Long) arguments[0], (Long) arguments[1], (Boolean) arguments[2], (List) arguments[3]));
        }
    }
}
