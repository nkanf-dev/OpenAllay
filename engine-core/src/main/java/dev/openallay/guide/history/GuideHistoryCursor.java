package dev.openallay.guide.history;

import java.util.UUID;

/** Stable exclusive request cursor inside one durable session. */
@dev.openallay.value.ValueType(GuideHistoryCursor.ValueSchemaProvider.class)
public final class GuideHistoryCursor {
    private final long sequence;
    private final UUID requestId;
    public GuideHistoryCursor(long sequence, UUID requestId) {

        if (sequence < 0) {
            throw new IllegalArgumentException("history sequence must not be negative");
        }
        java.util.Objects.requireNonNull(requestId, "requestId");

        this.sequence = sequence;
        this.requestId = requestId;
    }
    public long sequence() { return sequence; }
    public UUID requestId() { return requestId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryCursor)) return false;
        GuideHistoryCursor that = (GuideHistoryCursor) other;
        return sequence == that.sequence && java.util.Objects.equals(requestId, that.requestId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(sequence);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryCursor[sequence=" + sequence + ", requestId=" + requestId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryCursor> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryCursor.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryCursor>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryCursor.class, "sequence", GuideHistoryCursor::sequence), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryCursor.class, "requestId", GuideHistoryCursor::requestId)), arguments -> new GuideHistoryCursor((Long) arguments[0], (UUID) arguments[1]));
        }
    }
}
