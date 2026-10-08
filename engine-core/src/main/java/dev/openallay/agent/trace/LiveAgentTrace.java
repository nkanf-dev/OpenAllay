package dev.openallay.agent.trace;

import dev.openallay.agent.AgentState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@dev.openallay.value.ValueType(LiveAgentTrace.ValueSchemaProvider.class)
public final class LiveAgentTrace {
    private final UUID requestId;
    private final UUID actorId;
    private final String sessionId;
    private final Instant startedAt;
    private final Instant completedAt;
    private final AgentState finalState;
    private final List<LiveTraceEvent> events;
    private final String finalText;
    private final String errorCode;
    public LiveAgentTrace(UUID requestId, UUID actorId, String sessionId, Instant startedAt, Instant completedAt, AgentState finalState, List<LiveTraceEvent> events, String finalText, String errorCode) {

        events = dev.openallay.util.Java8Collections.listCopyOf(events);

        this.requestId = requestId;
        this.actorId = actorId;
        this.sessionId = sessionId;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.finalState = finalState;
        this.events = events;
        this.finalText = finalText;
        this.errorCode = errorCode;
    }
    public UUID requestId() { return requestId; }
    public UUID actorId() { return actorId; }
    public String sessionId() { return sessionId; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public AgentState finalState() { return finalState; }
    public List<LiveTraceEvent> events() { return events; }
    public String finalText() { return finalText; }
    public String errorCode() { return errorCode; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LiveAgentTrace)) return false;
        LiveAgentTrace that = (LiveAgentTrace) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(startedAt, that.startedAt) && java.util.Objects.equals(completedAt, that.completedAt) && java.util.Objects.equals(finalState, that.finalState) && java.util.Objects.equals(events, that.events) && java.util.Objects.equals(finalText, that.finalText) && java.util.Objects.equals(errorCode, that.errorCode);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(startedAt);
        hash = 31 * hash + java.util.Objects.hashCode(completedAt);
        hash = 31 * hash + java.util.Objects.hashCode(finalState);
        hash = 31 * hash + java.util.Objects.hashCode(events);
        hash = 31 * hash + java.util.Objects.hashCode(finalText);
        hash = 31 * hash + java.util.Objects.hashCode(errorCode);
        return hash;
    }
    @Override public String toString() { return "LiveAgentTrace[requestId=" + requestId + ", actorId=" + actorId + ", sessionId=" + sessionId + ", startedAt=" + startedAt + ", completedAt=" + completedAt + ", finalState=" + finalState + ", events=" + events + ", finalText=" + finalText + ", errorCode=" + errorCode + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<LiveAgentTrace> schema() {
            return new dev.openallay.value.ValueSchema<>(LiveAgentTrace.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<LiveAgentTrace>>asList(new dev.openallay.value.ValueSchema.Component<>(LiveAgentTrace.class, "requestId", LiveAgentTrace::requestId), new dev.openallay.value.ValueSchema.Component<>(LiveAgentTrace.class, "actorId", LiveAgentTrace::actorId), new dev.openallay.value.ValueSchema.Component<>(LiveAgentTrace.class, "sessionId", LiveAgentTrace::sessionId), new dev.openallay.value.ValueSchema.Component<>(LiveAgentTrace.class, "startedAt", LiveAgentTrace::startedAt), new dev.openallay.value.ValueSchema.Component<>(LiveAgentTrace.class, "completedAt", LiveAgentTrace::completedAt), new dev.openallay.value.ValueSchema.Component<>(LiveAgentTrace.class, "finalState", LiveAgentTrace::finalState), new dev.openallay.value.ValueSchema.Component<>(LiveAgentTrace.class, "events", LiveAgentTrace::events), new dev.openallay.value.ValueSchema.Component<>(LiveAgentTrace.class, "finalText", LiveAgentTrace::finalText), new dev.openallay.value.ValueSchema.Component<>(LiveAgentTrace.class, "errorCode", LiveAgentTrace::errorCode)), arguments -> new LiveAgentTrace((UUID) arguments[0], (UUID) arguments[1], (String) arguments[2], (Instant) arguments[3], (Instant) arguments[4], (AgentState) arguments[5], (List) arguments[6], (String) arguments[7], (String) arguments[8]));
        }
    }
}
