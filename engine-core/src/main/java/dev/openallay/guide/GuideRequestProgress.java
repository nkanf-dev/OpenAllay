package dev.openallay.guide;

import java.time.Instant;
import java.util.Objects;

/** Immutable request progress containing no provider, prompt, argument, or response content. */
@dev.openallay.value.ValueType(GuideRequestProgress.ValueSchemaProvider.class)
public final class GuideRequestProgress {
    private final GuideRequestPhase phase;
    private final Instant requestStartedAt;
    private final Instant phaseStartedAt;
    private final Instant lastProgressAt;
    private final int attempt;
    private final Instant retryAt;
    private final Instant deadlineAt;
    public GuideRequestProgress(GuideRequestPhase phase, Instant requestStartedAt, Instant phaseStartedAt, Instant lastProgressAt, int attempt, Instant retryAt, Instant deadlineAt) {

        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(requestStartedAt, "requestStartedAt");
        Objects.requireNonNull(phaseStartedAt, "phaseStartedAt");
        Objects.requireNonNull(lastProgressAt, "lastProgressAt");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
        if (phaseStartedAt.isBefore(requestStartedAt)
                || lastProgressAt.isBefore(phaseStartedAt)) {
            throw new IllegalArgumentException("request progress timestamps are out of order");
        }
        if (retryAt != null && retryAt.isBefore(lastProgressAt)) {
            throw new IllegalArgumentException("retryAt must not precede lastProgressAt");
        }
        if (deadlineAt != null && deadlineAt.isBefore(requestStartedAt)) {
            throw new IllegalArgumentException("deadlineAt must not precede requestStartedAt");
        }

        this.phase = phase;
        this.requestStartedAt = requestStartedAt;
        this.phaseStartedAt = phaseStartedAt;
        this.lastProgressAt = lastProgressAt;
        this.attempt = attempt;
        this.retryAt = retryAt;
        this.deadlineAt = deadlineAt;
    }
    public GuideRequestPhase phase() { return phase; }
    public Instant requestStartedAt() { return requestStartedAt; }
    public Instant phaseStartedAt() { return phaseStartedAt; }
    public Instant lastProgressAt() { return lastProgressAt; }
    public int attempt() { return attempt; }
    public Instant retryAt() { return retryAt; }
    public Instant deadlineAt() { return deadlineAt; }
public static GuideRequestProgress start(Instant now) {
        return new GuideRequestProgress(
                GuideRequestPhase.PREPARING, now, now, now, 0, null, null);
    }
public GuideRequestProgress advance(
            GuideRequestPhase nextPhase,
            Instant observedAt,
            int nextAttempt,
            Instant nextRetryAt,
            Instant nextDeadlineAt) {
        Objects.requireNonNull(nextPhase, "nextPhase");
        Objects.requireNonNull(observedAt, "observedAt");
        Instant monotonic = observedAt.isBefore(lastProgressAt) ? lastProgressAt : observedAt;
        Instant nextPhaseStarted = nextPhase == phase ? phaseStartedAt : monotonic;
        return new GuideRequestProgress(
                nextPhase,
                requestStartedAt,
                nextPhaseStarted,
                monotonic,
                nextAttempt,
                nextRetryAt,
                nextDeadlineAt);
    }
public GuideRequestProgress advance(GuideRequestPhase nextPhase, Instant observedAt) {
        return advance(nextPhase, observedAt, attempt, retryAt, deadlineAt);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideRequestProgress)) return false;
        GuideRequestProgress that = (GuideRequestProgress) other;
        return java.util.Objects.equals(phase, that.phase) && java.util.Objects.equals(requestStartedAt, that.requestStartedAt) && java.util.Objects.equals(phaseStartedAt, that.phaseStartedAt) && java.util.Objects.equals(lastProgressAt, that.lastProgressAt) && attempt == that.attempt && java.util.Objects.equals(retryAt, that.retryAt) && java.util.Objects.equals(deadlineAt, that.deadlineAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(phase);
        hash = 31 * hash + java.util.Objects.hashCode(requestStartedAt);
        hash = 31 * hash + java.util.Objects.hashCode(phaseStartedAt);
        hash = 31 * hash + java.util.Objects.hashCode(lastProgressAt);
        hash = 31 * hash + Integer.hashCode(attempt);
        hash = 31 * hash + java.util.Objects.hashCode(retryAt);
        hash = 31 * hash + java.util.Objects.hashCode(deadlineAt);
        return hash;
    }
    @Override public String toString() { return "GuideRequestProgress[phase=" + phase + ", requestStartedAt=" + requestStartedAt + ", phaseStartedAt=" + phaseStartedAt + ", lastProgressAt=" + lastProgressAt + ", attempt=" + attempt + ", retryAt=" + retryAt + ", deadlineAt=" + deadlineAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideRequestProgress> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideRequestProgress.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideRequestProgress>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideRequestProgress.class, "phase", GuideRequestProgress::phase), new dev.openallay.value.ValueSchema.Component<>(GuideRequestProgress.class, "requestStartedAt", GuideRequestProgress::requestStartedAt), new dev.openallay.value.ValueSchema.Component<>(GuideRequestProgress.class, "phaseStartedAt", GuideRequestProgress::phaseStartedAt), new dev.openallay.value.ValueSchema.Component<>(GuideRequestProgress.class, "lastProgressAt", GuideRequestProgress::lastProgressAt), new dev.openallay.value.ValueSchema.Component<>(GuideRequestProgress.class, "attempt", GuideRequestProgress::attempt), new dev.openallay.value.ValueSchema.Component<>(GuideRequestProgress.class, "retryAt", GuideRequestProgress::retryAt), new dev.openallay.value.ValueSchema.Component<>(GuideRequestProgress.class, "deadlineAt", GuideRequestProgress::deadlineAt)), arguments -> new GuideRequestProgress((GuideRequestPhase) arguments[0], (Instant) arguments[1], (Instant) arguments[2], (Instant) arguments[3], (Integer) arguments[4], (Instant) arguments[5], (Instant) arguments[6]));
        }
    }
}
