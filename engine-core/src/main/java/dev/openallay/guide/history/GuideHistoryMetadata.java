package dev.openallay.guide.history;

import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideUsageSnapshot;
import java.time.Instant;
import java.util.List;

/** Body-free startup projection for one player/world-or-server partition. */
@dev.openallay.value.ValueType(GuideHistoryMetadata.ValueSchemaProvider.class)
public final class GuideHistoryMetadata {
    private final GuideHistoryScope scope;
    private final String selectedSession;
    private final List<Session> sessions;
    private final Instant updatedAt;
    public GuideHistoryMetadata(GuideHistoryScope scope, String selectedSession, List<Session> sessions, Instant updatedAt) {

        java.util.Objects.requireNonNull(scope, "scope");
        if (selectedSession == null || selectedSession.isBlank()) {
            throw new IllegalArgumentException("selected session is required");
        }
        sessions = List.copyOf(sessions);
        if (sessions.stream().noneMatch(value -> value.sessionId().equals(selectedSession))) {
            throw new IllegalArgumentException("selected session is absent from metadata");
        }
        java.util.Objects.requireNonNull(updatedAt, "updatedAt");

        this.scope = scope;
        this.selectedSession = selectedSession;
        this.sessions = sessions;
        this.updatedAt = updatedAt;
    }
    public GuideHistoryScope scope() { return scope; }
    public String selectedSession() { return selectedSession; }
    public List<Session> sessions() { return sessions; }
    public Instant updatedAt() { return updatedAt; }
@dev.openallay.value.ValueType(Session.ValueSchemaProvider.class)
public static final class Session {
    private final String sessionId;
    private final int ordinal;
    private final GuideModelSelection modelSelection;
    private final long requestCount;
    private final GuideHistoryCursor first;
    private final GuideHistoryCursor last;
    private final GuideUsageSnapshot usage;
    private final GuideUsageSnapshot inheritedUsage;
    private final GuideUsageSnapshot controlUsage;
    private final int messageCount;
    public Session(String sessionId, int ordinal, GuideModelSelection modelSelection, long requestCount, GuideHistoryCursor first, GuideHistoryCursor last, GuideUsageSnapshot usage, GuideUsageSnapshot inheritedUsage, GuideUsageSnapshot controlUsage, int messageCount) {

            if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
                throw new IllegalArgumentException("invalid session ID");
            }
            if (ordinal < 0 || requestCount < 0 || messageCount < 0) {
                throw new IllegalArgumentException("session metadata count is invalid");
            }
            java.util.Objects.requireNonNull(modelSelection, "modelSelection");
            java.util.Objects.requireNonNull(usage, "usage");
            java.util.Objects.requireNonNull(inheritedUsage, "inheritedUsage");
            java.util.Objects.requireNonNull(controlUsage, "controlUsage");
            if (requestCount == 0 ? first != null || last != null : first == null || last == null) {
                throw new IllegalArgumentException("session cursor metadata is inconsistent");
            }

        this.sessionId = sessionId;
        this.ordinal = ordinal;
        this.modelSelection = modelSelection;
        this.requestCount = requestCount;
        this.first = first;
        this.last = last;
        this.usage = usage;
        this.inheritedUsage = inheritedUsage;
        this.controlUsage = controlUsage;
        this.messageCount = messageCount;
    }
    public String sessionId() { return sessionId; }
    public int ordinal() { return ordinal; }
    public GuideModelSelection modelSelection() { return modelSelection; }
    public long requestCount() { return requestCount; }
    public GuideHistoryCursor first() { return first; }
    public GuideHistoryCursor last() { return last; }
    public GuideUsageSnapshot usage() { return usage; }
    public GuideUsageSnapshot inheritedUsage() { return inheritedUsage; }
    public GuideUsageSnapshot controlUsage() { return controlUsage; }
    public int messageCount() { return messageCount; }
public Session(String sessionId, int ordinal, GuideModelSelection modelSelection,
                long requestCount, GuideHistoryCursor first, GuideHistoryCursor last) {
            this(sessionId, ordinal, modelSelection, requestCount, first, last,
                    requestCount == 0 ? GuideUsageSnapshot.empty() : GuideUsageSnapshot.unknown(),
                    GuideUsageSnapshot.empty(), GuideUsageSnapshot.empty(), 0);
        }
public Session(String sessionId, int ordinal, GuideModelSelection modelSelection,
                long requestCount, GuideHistoryCursor first, GuideHistoryCursor last, int messageCount) {
            this(sessionId, ordinal, modelSelection, requestCount, first, last,
                    requestCount == 0 ? GuideUsageSnapshot.empty() : GuideUsageSnapshot.unknown(),
                    GuideUsageSnapshot.empty(), GuideUsageSnapshot.empty(), messageCount);
        }
public Session(String sessionId, int ordinal, GuideModelSelection modelSelection,
                long requestCount, GuideHistoryCursor first, GuideHistoryCursor last,
                GuideUsageSnapshot usage, GuideUsageSnapshot inheritedUsage) {
            this(sessionId, ordinal, modelSelection, requestCount, first, last,
                    usage, inheritedUsage, GuideUsageSnapshot.empty(), 0);
        }
public Session(String sessionId, int ordinal, GuideModelSelection modelSelection,
                long requestCount, GuideHistoryCursor first, GuideHistoryCursor last,
                GuideUsageSnapshot usage, GuideUsageSnapshot inheritedUsage, GuideUsageSnapshot controlUsage) {
            this(sessionId, ordinal, modelSelection, requestCount, first, last,
                    usage, inheritedUsage, controlUsage, 0);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Session)) return false;
        Session that = (Session) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && ordinal == that.ordinal && java.util.Objects.equals(modelSelection, that.modelSelection) && requestCount == that.requestCount && java.util.Objects.equals(first, that.first) && java.util.Objects.equals(last, that.last) && java.util.Objects.equals(usage, that.usage) && java.util.Objects.equals(inheritedUsage, that.inheritedUsage) && java.util.Objects.equals(controlUsage, that.controlUsage) && messageCount == that.messageCount;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(modelSelection);
        hash = 31 * hash + Long.hashCode(requestCount);
        hash = 31 * hash + java.util.Objects.hashCode(first);
        hash = 31 * hash + java.util.Objects.hashCode(last);
        hash = 31 * hash + java.util.Objects.hashCode(usage);
        hash = 31 * hash + java.util.Objects.hashCode(inheritedUsage);
        hash = 31 * hash + java.util.Objects.hashCode(controlUsage);
        hash = 31 * hash + Integer.hashCode(messageCount);
        return hash;
    }
    @Override public String toString() { return "Session[sessionId=" + sessionId + ", ordinal=" + ordinal + ", modelSelection=" + modelSelection + ", requestCount=" + requestCount + ", first=" + first + ", last=" + last + ", usage=" + usage + ", inheritedUsage=" + inheritedUsage + ", controlUsage=" + controlUsage + ", messageCount=" + messageCount + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Session> schema() {
            return new dev.openallay.value.ValueSchema<>(Session.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Session>>asList(new dev.openallay.value.ValueSchema.Component<>(Session.class, "sessionId", Session::sessionId), new dev.openallay.value.ValueSchema.Component<>(Session.class, "ordinal", Session::ordinal), new dev.openallay.value.ValueSchema.Component<>(Session.class, "modelSelection", Session::modelSelection), new dev.openallay.value.ValueSchema.Component<>(Session.class, "requestCount", Session::requestCount), new dev.openallay.value.ValueSchema.Component<>(Session.class, "first", Session::first), new dev.openallay.value.ValueSchema.Component<>(Session.class, "last", Session::last), new dev.openallay.value.ValueSchema.Component<>(Session.class, "usage", Session::usage), new dev.openallay.value.ValueSchema.Component<>(Session.class, "inheritedUsage", Session::inheritedUsage), new dev.openallay.value.ValueSchema.Component<>(Session.class, "controlUsage", Session::controlUsage), new dev.openallay.value.ValueSchema.Component<>(Session.class, "messageCount", Session::messageCount)), arguments -> new Session((String) arguments[0], (Integer) arguments[1], (GuideModelSelection) arguments[2], (Long) arguments[3], (GuideHistoryCursor) arguments[4], (GuideHistoryCursor) arguments[5], (GuideUsageSnapshot) arguments[6], (GuideUsageSnapshot) arguments[7], (GuideUsageSnapshot) arguments[8], (Integer) arguments[9]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryMetadata)) return false;
        GuideHistoryMetadata that = (GuideHistoryMetadata) other;
        return java.util.Objects.equals(scope, that.scope) && java.util.Objects.equals(selectedSession, that.selectedSession) && java.util.Objects.equals(sessions, that.sessions) && java.util.Objects.equals(updatedAt, that.updatedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(scope);
        hash = 31 * hash + java.util.Objects.hashCode(selectedSession);
        hash = 31 * hash + java.util.Objects.hashCode(sessions);
        hash = 31 * hash + java.util.Objects.hashCode(updatedAt);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryMetadata[scope=" + scope + ", selectedSession=" + selectedSession + ", sessions=" + sessions + ", updatedAt=" + updatedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryMetadata> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryMetadata.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryMetadata>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryMetadata.class, "scope", GuideHistoryMetadata::scope), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryMetadata.class, "selectedSession", GuideHistoryMetadata::selectedSession), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryMetadata.class, "sessions", GuideHistoryMetadata::sessions), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryMetadata.class, "updatedAt", GuideHistoryMetadata::updatedAt)), arguments -> new GuideHistoryMetadata((GuideHistoryScope) arguments[0], (String) arguments[1], (List) arguments[2], (Instant) arguments[3]));
        }
    }
}
