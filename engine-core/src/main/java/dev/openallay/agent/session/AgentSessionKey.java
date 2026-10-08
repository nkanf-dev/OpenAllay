package dev.openallay.agent.session;

import java.util.Objects;
import java.util.UUID;

@dev.openallay.value.ValueType(AgentSessionKey.ValueSchemaProvider.class)
public final class AgentSessionKey {
    private final UUID actorId;
    private final String sessionId;
    public AgentSessionKey(UUID actorId, String sessionId) {

        Objects.requireNonNull(actorId, "actorId");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid Agent session ID: " + sessionId);
        }

        this.actorId = actorId;
        this.sessionId = sessionId;
    }
    public UUID actorId() { return actorId; }
    public String sessionId() { return sessionId; }
public String schedulingKey() {
        return actorId + ":" + sessionId;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AgentSessionKey)) return false;
        AgentSessionKey that = (AgentSessionKey) other;
        return java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(sessionId, that.sessionId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        return hash;
    }
    @Override public String toString() { return "AgentSessionKey[actorId=" + actorId + ", sessionId=" + sessionId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<AgentSessionKey> schema() {
            return new dev.openallay.value.ValueSchema<>(AgentSessionKey.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<AgentSessionKey>>asList(new dev.openallay.value.ValueSchema.Component<>(AgentSessionKey.class, "actorId", AgentSessionKey::actorId), new dev.openallay.value.ValueSchema.Component<>(AgentSessionKey.class, "sessionId", AgentSessionKey::sessionId)), arguments -> new AgentSessionKey((UUID) arguments[0], (String) arguments[1]));
        }
    }
}
