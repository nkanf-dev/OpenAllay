package dev.openallay.guide;

import java.util.Objects;

@dev.openallay.value.ValueType(GuidePersistenceSnapshot.ValueSchemaProvider.class)
public final class GuidePersistenceSnapshot {
    private final State state;
    private final long submittedGeneration;
    private final long committedGeneration;
    private final GuideFailure failure;
    public GuidePersistenceSnapshot(State state, long submittedGeneration, long committedGeneration, GuideFailure failure) {

        Objects.requireNonNull(state, "state");
        if (submittedGeneration < 0
                || committedGeneration < 0
                || committedGeneration > submittedGeneration) {
            throw new IllegalArgumentException("persistence generations are invalid");
        }
        if (state == State.SAVING && submittedGeneration <= committedGeneration) {
            throw new IllegalArgumentException("saving state requires an uncommitted generation");
        }
        if (state == State.AVAILABLE && submittedGeneration != committedGeneration) {
            throw new IllegalArgumentException("available state requires all writes committed");
        }
        if (state == State.UNAVAILABLE && failure == null) {
            throw new IllegalArgumentException("unavailable persistence requires a failure");
        }
        if (state != State.UNAVAILABLE && failure != null) {
            throw new IllegalArgumentException("only unavailable persistence may expose a failure");
        }

        this.state = state;
        this.submittedGeneration = submittedGeneration;
        this.committedGeneration = committedGeneration;
        this.failure = failure;
    }
    public State state() { return state; }
    public long submittedGeneration() { return submittedGeneration; }
    public long committedGeneration() { return committedGeneration; }
    public GuideFailure failure() { return failure; }
public enum State {
        DISABLED,
        LOADING,
        SAVING,
        AVAILABLE,
        UNAVAILABLE
    }
public static GuidePersistenceSnapshot disabled() {
        return new GuidePersistenceSnapshot(State.DISABLED, 0, 0, null);
    }
public static GuidePersistenceSnapshot loading() {
        return new GuidePersistenceSnapshot(State.LOADING, 0, 0, null);
    }
public static GuidePersistenceSnapshot available(long generation) {
        return new GuidePersistenceSnapshot(State.AVAILABLE, generation, generation, null);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuidePersistenceSnapshot)) return false;
        GuidePersistenceSnapshot that = (GuidePersistenceSnapshot) other;
        return java.util.Objects.equals(state, that.state) && submittedGeneration == that.submittedGeneration && committedGeneration == that.committedGeneration && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + Long.hashCode(submittedGeneration);
        hash = 31 * hash + Long.hashCode(committedGeneration);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "GuidePersistenceSnapshot[state=" + state + ", submittedGeneration=" + submittedGeneration + ", committedGeneration=" + committedGeneration + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuidePersistenceSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(GuidePersistenceSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuidePersistenceSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(GuidePersistenceSnapshot.class, "state", GuidePersistenceSnapshot::state), new dev.openallay.value.ValueSchema.Component<>(GuidePersistenceSnapshot.class, "submittedGeneration", GuidePersistenceSnapshot::submittedGeneration), new dev.openallay.value.ValueSchema.Component<>(GuidePersistenceSnapshot.class, "committedGeneration", GuidePersistenceSnapshot::committedGeneration), new dev.openallay.value.ValueSchema.Component<>(GuidePersistenceSnapshot.class, "failure", GuidePersistenceSnapshot::failure)), arguments -> new GuidePersistenceSnapshot((State) arguments[0], (Long) arguments[1], (Long) arguments[2], (GuideFailure) arguments[3]));
        }
    }
}
