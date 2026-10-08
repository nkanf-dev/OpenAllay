package dev.openallay.guide;

import dev.openallay.guide.history.GuideHistoryActivity;
import dev.openallay.guide.history.GuideHistoryScope;
import java.util.Objects;
import java.util.Optional;

/** Atomic manager view for settings adapters; it carries no database path or raw scope ID. */
@dev.openallay.value.ValueType(GuideHistorySettingsSnapshot.ValueSchemaProvider.class)
public final class GuideHistorySettingsSnapshot {
    private final boolean configured;
    private final Optional<GuideSnapshot> guide;
    private final GuideHistoryActivity activity;
    private final Optional<GuideHistoryScope.Kind> scopeKind;
    private final Long estimatedContextTokens;
    public GuideHistorySettingsSnapshot(boolean configured, Optional<GuideSnapshot> guide, GuideHistoryActivity activity, Optional<GuideHistoryScope.Kind> scopeKind, Long estimatedContextTokens) {

        guide = Objects.requireNonNull(guide, "guide");
        Objects.requireNonNull(activity, "activity");
        scopeKind = Objects.requireNonNull(scopeKind, "scopeKind");
        if (estimatedContextTokens != null && estimatedContextTokens < 0) {
            throw new IllegalArgumentException("Context estimate must not be negative");
        }
        if (guide.isPresent() != scopeKind.isPresent()) {
            throw new IllegalArgumentException(
                    "connected history settings require one friendly scope kind");
        }
        if (!configured && (guide.isPresent() || !activity.idleForDeletion())) {
            throw new IllegalArgumentException(
                    "unconfigured history cannot publish connection activity");
        }

        this.configured = configured;
        this.guide = guide;
        this.activity = activity;
        this.scopeKind = scopeKind;
        this.estimatedContextTokens = estimatedContextTokens;
    }
    public boolean configured() { return configured; }
    public Optional<GuideSnapshot> guide() { return guide; }
    public GuideHistoryActivity activity() { return activity; }
    public Optional<GuideHistoryScope.Kind> scopeKind() { return scopeKind; }
    public Long estimatedContextTokens() { return estimatedContextTokens; }
public GuideHistorySettingsSnapshot(boolean configured, Optional<GuideSnapshot> guide,
            GuideHistoryActivity activity, Optional<GuideHistoryScope.Kind> scopeKind) {
        this(configured, guide, activity, scopeKind, null);
    }
public static GuideHistorySettingsSnapshot unavailable(boolean configured) {
        return new GuideHistorySettingsSnapshot(
                configured,
                Optional.empty(),
                GuideHistoryActivity.idle(),
                Optional.empty());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistorySettingsSnapshot)) return false;
        GuideHistorySettingsSnapshot that = (GuideHistorySettingsSnapshot) other;
        return configured == that.configured && java.util.Objects.equals(guide, that.guide) && java.util.Objects.equals(activity, that.activity) && java.util.Objects.equals(scopeKind, that.scopeKind) && java.util.Objects.equals(estimatedContextTokens, that.estimatedContextTokens);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(configured);
        hash = 31 * hash + java.util.Objects.hashCode(guide);
        hash = 31 * hash + java.util.Objects.hashCode(activity);
        hash = 31 * hash + java.util.Objects.hashCode(scopeKind);
        hash = 31 * hash + java.util.Objects.hashCode(estimatedContextTokens);
        return hash;
    }
    @Override public String toString() { return "GuideHistorySettingsSnapshot[configured=" + configured + ", guide=" + guide + ", activity=" + activity + ", scopeKind=" + scopeKind + ", estimatedContextTokens=" + estimatedContextTokens + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistorySettingsSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistorySettingsSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistorySettingsSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistorySettingsSnapshot.class, "configured", GuideHistorySettingsSnapshot::configured), new dev.openallay.value.ValueSchema.Component<>(GuideHistorySettingsSnapshot.class, "guide", GuideHistorySettingsSnapshot::guide), new dev.openallay.value.ValueSchema.Component<>(GuideHistorySettingsSnapshot.class, "activity", GuideHistorySettingsSnapshot::activity), new dev.openallay.value.ValueSchema.Component<>(GuideHistorySettingsSnapshot.class, "scopeKind", GuideHistorySettingsSnapshot::scopeKind), new dev.openallay.value.ValueSchema.Component<>(GuideHistorySettingsSnapshot.class, "estimatedContextTokens", GuideHistorySettingsSnapshot::estimatedContextTokens)), arguments -> new GuideHistorySettingsSnapshot((Boolean) arguments[0], (Optional) arguments[1], (GuideHistoryActivity) arguments[2], (Optional) arguments[3], (Long) arguments[4]));
        }
    }
}
