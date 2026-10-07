package dev.openallay.guide.history;

/** Ordered history-repository activity used for non-queueing deletion gates. */
@dev.openallay.value.ValueType(GuideHistoryActivity.ValueSchemaProvider.class)
public final class GuideHistoryActivity {
    private final int pendingWrites;
    private final boolean deleting;
    public GuideHistoryActivity(int pendingWrites, boolean deleting) {

        if (pendingWrites < 0) {
            throw new IllegalArgumentException("pendingWrites must not be negative");
        }

        this.pendingWrites = pendingWrites;
        this.deleting = deleting;
    }
    public int pendingWrites() { return pendingWrites; }
    public boolean deleting() { return deleting; }
public boolean idleForDeletion() {
        return pendingWrites == 0 && !deleting;
    }
public static GuideHistoryActivity idle() {
        return new GuideHistoryActivity(0, false);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryActivity)) return false;
        GuideHistoryActivity that = (GuideHistoryActivity) other;
        return pendingWrites == that.pendingWrites && deleting == that.deleting;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(pendingWrites);
        hash = 31 * hash + Boolean.hashCode(deleting);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryActivity[pendingWrites=" + pendingWrites + ", deleting=" + deleting + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryActivity> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryActivity.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryActivity>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryActivity.class, "pendingWrites", GuideHistoryActivity::pendingWrites), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryActivity.class, "deleting", GuideHistoryActivity::deleting)), arguments -> new GuideHistoryActivity((Integer) arguments[0], (Boolean) arguments[1]));
        }
    }
}
