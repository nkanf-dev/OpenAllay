package dev.openallay.guide.history;

/** Viewport paging is independent from model-context budgeting. */
@dev.openallay.value.ValueType(GuideHistoryPageRequest.ValueSchemaProvider.class)
public final class GuideHistoryPageRequest {
    private final GuideHistoryScope scope;
    private final String sessionId;
    private final Direction direction;
    private final GuideHistoryCursor cursor;
    private final int count;
    public GuideHistoryPageRequest(GuideHistoryScope scope, String sessionId, Direction direction, GuideHistoryCursor cursor, int count) {

        java.util.Objects.requireNonNull(scope, "scope");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid session ID");
        }
        java.util.Objects.requireNonNull(direction, "direction");
        if (direction == Direction.NEWEST && cursor != null
                || direction != Direction.NEWEST && cursor == null) {
            throw new IllegalArgumentException("history page cursor does not match direction");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("history page count must be positive");
        }

        this.scope = scope;
        this.sessionId = sessionId;
        this.direction = direction;
        this.cursor = cursor;
        this.count = count;
    }
    public GuideHistoryScope scope() { return scope; }
    public String sessionId() { return sessionId; }
    public Direction direction() { return direction; }
    public GuideHistoryCursor cursor() { return cursor; }
    public int count() { return count; }
public enum Direction { NEWEST, BEFORE, AFTER }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryPageRequest)) return false;
        GuideHistoryPageRequest that = (GuideHistoryPageRequest) other;
        return java.util.Objects.equals(scope, that.scope) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(direction, that.direction) && java.util.Objects.equals(cursor, that.cursor) && count == that.count;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(scope);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(direction);
        hash = 31 * hash + java.util.Objects.hashCode(cursor);
        hash = 31 * hash + Integer.hashCode(count);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryPageRequest[scope=" + scope + ", sessionId=" + sessionId + ", direction=" + direction + ", cursor=" + cursor + ", count=" + count + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryPageRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryPageRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryPageRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPageRequest.class, "scope", GuideHistoryPageRequest::scope), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPageRequest.class, "sessionId", GuideHistoryPageRequest::sessionId), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPageRequest.class, "direction", GuideHistoryPageRequest::direction), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPageRequest.class, "cursor", GuideHistoryPageRequest::cursor), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPageRequest.class, "count", GuideHistoryPageRequest::count)), arguments -> new GuideHistoryPageRequest((GuideHistoryScope) arguments[0], (String) arguments[1], (Direction) arguments[2], (GuideHistoryCursor) arguments[3], (Integer) arguments[4]));
        }
    }
}
