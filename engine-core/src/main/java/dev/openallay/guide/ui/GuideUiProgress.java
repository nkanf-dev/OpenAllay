package dev.openallay.guide.ui;

import dev.openallay.guide.GuideRequestPhase;
import dev.openallay.guide.GuideRequestProgress;
import java.time.Instant;
import java.util.Objects;

/** Redacted player-facing projection for the fixed request progress strip. */
@dev.openallay.value.ValueType(GuideUiProgress.ValueSchemaProvider.class)
public final class GuideUiProgress {
    private final GuideRequestPhase phase;
    private final String activityTranslationKey;
    private final Instant requestStartedAt;
    private final Instant phaseStartedAt;
    private final Instant lastProgressAt;
    private final int attempt;
    private final Instant retryAt;
    private final Instant deadlineAt;
    public GuideUiProgress(GuideRequestPhase phase, String activityTranslationKey, Instant requestStartedAt, Instant phaseStartedAt, Instant lastProgressAt, int attempt, Instant retryAt, Instant deadlineAt) {

        Objects.requireNonNull(phase, "phase");
        if (activityTranslationKey == null || activityTranslationKey.isBlank()) {
            throw new IllegalArgumentException("progress translation key must not be blank");
        }
        Objects.requireNonNull(requestStartedAt, "requestStartedAt");
        Objects.requireNonNull(phaseStartedAt, "phaseStartedAt");
        Objects.requireNonNull(lastProgressAt, "lastProgressAt");
        if (attempt < 0) throw new IllegalArgumentException("attempt must not be negative");

        this.phase = phase;
        this.activityTranslationKey = activityTranslationKey;
        this.requestStartedAt = requestStartedAt;
        this.phaseStartedAt = phaseStartedAt;
        this.lastProgressAt = lastProgressAt;
        this.attempt = attempt;
        this.retryAt = retryAt;
        this.deadlineAt = deadlineAt;
    }
    public GuideRequestPhase phase() { return phase; }
    public String activityTranslationKey() { return activityTranslationKey; }
    public Instant requestStartedAt() { return requestStartedAt; }
    public Instant phaseStartedAt() { return phaseStartedAt; }
    public Instant lastProgressAt() { return lastProgressAt; }
    public int attempt() { return attempt; }
    public Instant retryAt() { return retryAt; }
    public Instant deadlineAt() { return deadlineAt; }
public static GuideUiProgress from(GuideRequestProgress progress) {
        Objects.requireNonNull(progress, "progress");
        return new GuideUiProgress(
                progress.phase(),
                switch (progress.phase()) {
                    case PREPARING -> "screen.openallay.progress.preparing";
                    case CONTEXT_LOADING -> "screen.openallay.progress.context_loading";
                    case COMPACTING -> "screen.openallay.progress.compacting";
                    case ENDPOINT_WAIT -> "screen.openallay.progress.endpoint_wait";
                    case MODEL_WAIT -> "screen.openallay.progress.model_wait";
                    case RESPONSE_STREAMING -> "screen.openallay.progress.streaming";
                    case TOOL_WAIT -> "screen.openallay.progress.tool_wait";
                    case COMPLETING -> "screen.openallay.progress.completing";
                },
                progress.requestStartedAt(),
                progress.phaseStartedAt(),
                progress.lastProgressAt(),
                progress.attempt(),
                progress.retryAt(),
                progress.deadlineAt());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideUiProgress)) return false;
        GuideUiProgress that = (GuideUiProgress) other;
        return java.util.Objects.equals(phase, that.phase) && java.util.Objects.equals(activityTranslationKey, that.activityTranslationKey) && java.util.Objects.equals(requestStartedAt, that.requestStartedAt) && java.util.Objects.equals(phaseStartedAt, that.phaseStartedAt) && java.util.Objects.equals(lastProgressAt, that.lastProgressAt) && attempt == that.attempt && java.util.Objects.equals(retryAt, that.retryAt) && java.util.Objects.equals(deadlineAt, that.deadlineAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(phase);
        hash = 31 * hash + java.util.Objects.hashCode(activityTranslationKey);
        hash = 31 * hash + java.util.Objects.hashCode(requestStartedAt);
        hash = 31 * hash + java.util.Objects.hashCode(phaseStartedAt);
        hash = 31 * hash + java.util.Objects.hashCode(lastProgressAt);
        hash = 31 * hash + Integer.hashCode(attempt);
        hash = 31 * hash + java.util.Objects.hashCode(retryAt);
        hash = 31 * hash + java.util.Objects.hashCode(deadlineAt);
        return hash;
    }
    @Override public String toString() { return "GuideUiProgress[phase=" + phase + ", activityTranslationKey=" + activityTranslationKey + ", requestStartedAt=" + requestStartedAt + ", phaseStartedAt=" + phaseStartedAt + ", lastProgressAt=" + lastProgressAt + ", attempt=" + attempt + ", retryAt=" + retryAt + ", deadlineAt=" + deadlineAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideUiProgress> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideUiProgress.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideUiProgress>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideUiProgress.class, "phase", GuideUiProgress::phase), new dev.openallay.value.ValueSchema.Component<>(GuideUiProgress.class, "activityTranslationKey", GuideUiProgress::activityTranslationKey), new dev.openallay.value.ValueSchema.Component<>(GuideUiProgress.class, "requestStartedAt", GuideUiProgress::requestStartedAt), new dev.openallay.value.ValueSchema.Component<>(GuideUiProgress.class, "phaseStartedAt", GuideUiProgress::phaseStartedAt), new dev.openallay.value.ValueSchema.Component<>(GuideUiProgress.class, "lastProgressAt", GuideUiProgress::lastProgressAt), new dev.openallay.value.ValueSchema.Component<>(GuideUiProgress.class, "attempt", GuideUiProgress::attempt), new dev.openallay.value.ValueSchema.Component<>(GuideUiProgress.class, "retryAt", GuideUiProgress::retryAt), new dev.openallay.value.ValueSchema.Component<>(GuideUiProgress.class, "deadlineAt", GuideUiProgress::deadlineAt)), arguments -> new GuideUiProgress((GuideRequestPhase) arguments[0], (String) arguments[1], (Instant) arguments[2], (Instant) arguments[3], (Instant) arguments[4], (Integer) arguments[5], (Instant) arguments[6], (Instant) arguments[7]));
        }
    }
}
