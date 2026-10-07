package dev.openallay.settings.history;

import java.util.Objects;

/** Actor-safe history administration state with no actor, scope, path, or SQL identity. */
@dev.openallay.value.ValueType(HistorySettingsView.ValueSchemaProvider.class)
public final class HistorySettingsView {
    private final ConnectionKind connectionKind;
    private final Health health;
    private final int pendingWrites;
    private final boolean deleting;
    private final long activeRequests;
    private final boolean currentDeleteAvailable;
    private final boolean actorDeleteAvailable;
    private final boolean databaseResetAvailable;
    public HistorySettingsView(ConnectionKind connectionKind, Health health, int pendingWrites, boolean deleting, long activeRequests, boolean currentDeleteAvailable, boolean actorDeleteAvailable, boolean databaseResetAvailable) {

        Objects.requireNonNull(connectionKind, "connectionKind");
        Objects.requireNonNull(health, "health");
        if (pendingWrites < 0 || activeRequests < 0) {
            throw new IllegalArgumentException("history activity must not be negative");
        }
        if (connectionKind == ConnectionKind.NONE
                && (health != Health.NOT_CONNECTED
                        || currentDeleteAvailable
                        || actorDeleteAvailable
                        || databaseResetAvailable)) {
            throw new IllegalArgumentException("disconnected history has no actions");
        }

        this.connectionKind = connectionKind;
        this.health = health;
        this.pendingWrites = pendingWrites;
        this.deleting = deleting;
        this.activeRequests = activeRequests;
        this.currentDeleteAvailable = currentDeleteAvailable;
        this.actorDeleteAvailable = actorDeleteAvailable;
        this.databaseResetAvailable = databaseResetAvailable;
    }
    public ConnectionKind connectionKind() { return connectionKind; }
    public Health health() { return health; }
    public int pendingWrites() { return pendingWrites; }
    public boolean deleting() { return deleting; }
    public long activeRequests() { return activeRequests; }
    public boolean currentDeleteAvailable() { return currentDeleteAvailable; }
    public boolean actorDeleteAvailable() { return actorDeleteAvailable; }
    public boolean databaseResetAvailable() { return databaseResetAvailable; }
public enum ConnectionKind {
        NONE,
        SINGLEPLAYER_WORLD,
        MULTIPLAYER_SERVER
    }
public enum Health {
        READY,
        WORKING,
        ATTENTION,
        UNAVAILABLE,
        NOT_CONNECTED
    }
public static HistorySettingsView disconnected() {
        return new HistorySettingsView(
                ConnectionKind.NONE,
                Health.NOT_CONNECTED,
                0,
                false,
                0,
                false,
                false,
                false);
    }
public static HistorySettingsView available(ConnectionKind connectionKind) {
        if (connectionKind == ConnectionKind.NONE) {
            throw new IllegalArgumentException("available history requires a connection");
        }
        return new HistorySettingsView(
                connectionKind,
                Health.READY,
                0,
                false,
                0,
                true,
                true,
                true);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HistorySettingsView)) return false;
        HistorySettingsView that = (HistorySettingsView) other;
        return java.util.Objects.equals(connectionKind, that.connectionKind) && java.util.Objects.equals(health, that.health) && pendingWrites == that.pendingWrites && deleting == that.deleting && activeRequests == that.activeRequests && currentDeleteAvailable == that.currentDeleteAvailable && actorDeleteAvailable == that.actorDeleteAvailable && databaseResetAvailable == that.databaseResetAvailable;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(connectionKind);
        hash = 31 * hash + java.util.Objects.hashCode(health);
        hash = 31 * hash + Integer.hashCode(pendingWrites);
        hash = 31 * hash + Boolean.hashCode(deleting);
        hash = 31 * hash + Long.hashCode(activeRequests);
        hash = 31 * hash + Boolean.hashCode(currentDeleteAvailable);
        hash = 31 * hash + Boolean.hashCode(actorDeleteAvailable);
        hash = 31 * hash + Boolean.hashCode(databaseResetAvailable);
        return hash;
    }
    @Override public String toString() { return "HistorySettingsView[connectionKind=" + connectionKind + ", health=" + health + ", pendingWrites=" + pendingWrites + ", deleting=" + deleting + ", activeRequests=" + activeRequests + ", currentDeleteAvailable=" + currentDeleteAvailable + ", actorDeleteAvailable=" + actorDeleteAvailable + ", databaseResetAvailable=" + databaseResetAvailable + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<HistorySettingsView> schema() {
            return new dev.openallay.value.ValueSchema<>(HistorySettingsView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<HistorySettingsView>>asList(new dev.openallay.value.ValueSchema.Component<>(HistorySettingsView.class, "connectionKind", HistorySettingsView::connectionKind), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsView.class, "health", HistorySettingsView::health), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsView.class, "pendingWrites", HistorySettingsView::pendingWrites), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsView.class, "deleting", HistorySettingsView::deleting), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsView.class, "activeRequests", HistorySettingsView::activeRequests), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsView.class, "currentDeleteAvailable", HistorySettingsView::currentDeleteAvailable), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsView.class, "actorDeleteAvailable", HistorySettingsView::actorDeleteAvailable), new dev.openallay.value.ValueSchema.Component<>(HistorySettingsView.class, "databaseResetAvailable", HistorySettingsView::databaseResetAvailable)), arguments -> new HistorySettingsView((ConnectionKind) arguments[0], (Health) arguments[1], (Integer) arguments[2], (Boolean) arguments[3], (Long) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (Boolean) arguments[7]));
        }
    }
}
