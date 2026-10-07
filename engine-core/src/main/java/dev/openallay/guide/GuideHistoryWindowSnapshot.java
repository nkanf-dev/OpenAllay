package dev.openallay.guide;

import dev.openallay.guide.history.GuideHistoryCursor;

/** Immutable viewport/history metadata; request bodies remain in the session page only. */
@dev.openallay.value.ValueType(GuideHistoryWindowSnapshot.ValueSchemaProvider.class)
public final class GuideHistoryWindowSnapshot {
    private final long totalRequests;
    private final GuideHistoryCursor firstAvailable;
    private final GuideHistoryCursor lastAvailable;
    private final GuideHistoryCursor firstLoaded;
    private final GuideHistoryCursor lastLoaded;
    private final boolean hasEarlier;
    private final boolean hasLater;
    private final GuideHistoryPageState state;
    private final long generation;
    private final GuideFailure failure;
    public GuideHistoryWindowSnapshot(long totalRequests, GuideHistoryCursor firstAvailable, GuideHistoryCursor lastAvailable, GuideHistoryCursor firstLoaded, GuideHistoryCursor lastLoaded, boolean hasEarlier, boolean hasLater, GuideHistoryPageState state, long generation, GuideFailure failure) {

        if (totalRequests < 0 || generation < 0) {
            throw new IllegalArgumentException("history window counters are invalid");
        }
        java.util.Objects.requireNonNull(state, "state");
        if (state == GuideHistoryPageState.FAILED && failure == null
                || state != GuideHistoryPageState.FAILED && failure != null) {
            throw new IllegalArgumentException("history window failure state is inconsistent");
        }
        if (totalRequests == 0
                && (firstAvailable != null || lastAvailable != null
                        || firstLoaded != null || lastLoaded != null
                        || hasEarlier || hasLater)) {
            throw new IllegalArgumentException("empty history window has cursors");
        }

        this.totalRequests = totalRequests;
        this.firstAvailable = firstAvailable;
        this.lastAvailable = lastAvailable;
        this.firstLoaded = firstLoaded;
        this.lastLoaded = lastLoaded;
        this.hasEarlier = hasEarlier;
        this.hasLater = hasLater;
        this.state = state;
        this.generation = generation;
        this.failure = failure;
    }
    public long totalRequests() { return totalRequests; }
    public GuideHistoryCursor firstAvailable() { return firstAvailable; }
    public GuideHistoryCursor lastAvailable() { return lastAvailable; }
    public GuideHistoryCursor firstLoaded() { return firstLoaded; }
    public GuideHistoryCursor lastLoaded() { return lastLoaded; }
    public boolean hasEarlier() { return hasEarlier; }
    public boolean hasLater() { return hasLater; }
    public GuideHistoryPageState state() { return state; }
    public long generation() { return generation; }
    public GuideFailure failure() { return failure; }
public static GuideHistoryWindowSnapshot disabled(long loadedRequests) {
        return new GuideHistoryWindowSnapshot(
                loadedRequests, null, null, null, null,
                false, false, GuideHistoryPageState.IDLE, 0, null);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryWindowSnapshot)) return false;
        GuideHistoryWindowSnapshot that = (GuideHistoryWindowSnapshot) other;
        return totalRequests == that.totalRequests && java.util.Objects.equals(firstAvailable, that.firstAvailable) && java.util.Objects.equals(lastAvailable, that.lastAvailable) && java.util.Objects.equals(firstLoaded, that.firstLoaded) && java.util.Objects.equals(lastLoaded, that.lastLoaded) && hasEarlier == that.hasEarlier && hasLater == that.hasLater && java.util.Objects.equals(state, that.state) && generation == that.generation && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(totalRequests);
        hash = 31 * hash + java.util.Objects.hashCode(firstAvailable);
        hash = 31 * hash + java.util.Objects.hashCode(lastAvailable);
        hash = 31 * hash + java.util.Objects.hashCode(firstLoaded);
        hash = 31 * hash + java.util.Objects.hashCode(lastLoaded);
        hash = 31 * hash + Boolean.hashCode(hasEarlier);
        hash = 31 * hash + Boolean.hashCode(hasLater);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryWindowSnapshot[totalRequests=" + totalRequests + ", firstAvailable=" + firstAvailable + ", lastAvailable=" + lastAvailable + ", firstLoaded=" + firstLoaded + ", lastLoaded=" + lastLoaded + ", hasEarlier=" + hasEarlier + ", hasLater=" + hasLater + ", state=" + state + ", generation=" + generation + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryWindowSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryWindowSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryWindowSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryWindowSnapshot.class, "totalRequests", GuideHistoryWindowSnapshot::totalRequests), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryWindowSnapshot.class, "firstAvailable", GuideHistoryWindowSnapshot::firstAvailable), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryWindowSnapshot.class, "lastAvailable", GuideHistoryWindowSnapshot::lastAvailable), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryWindowSnapshot.class, "firstLoaded", GuideHistoryWindowSnapshot::firstLoaded), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryWindowSnapshot.class, "lastLoaded", GuideHistoryWindowSnapshot::lastLoaded), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryWindowSnapshot.class, "hasEarlier", GuideHistoryWindowSnapshot::hasEarlier), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryWindowSnapshot.class, "hasLater", GuideHistoryWindowSnapshot::hasLater), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryWindowSnapshot.class, "state", GuideHistoryWindowSnapshot::state), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryWindowSnapshot.class, "generation", GuideHistoryWindowSnapshot::generation), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryWindowSnapshot.class, "failure", GuideHistoryWindowSnapshot::failure)), arguments -> new GuideHistoryWindowSnapshot((Long) arguments[0], (GuideHistoryCursor) arguments[1], (GuideHistoryCursor) arguments[2], (GuideHistoryCursor) arguments[3], (GuideHistoryCursor) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (GuideHistoryPageState) arguments[7], (Long) arguments[8], (GuideFailure) arguments[9]));
        }
    }
}
