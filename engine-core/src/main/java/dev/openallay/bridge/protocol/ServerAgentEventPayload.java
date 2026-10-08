package dev.openallay.bridge.protocol;

import java.util.UUID;

@dev.openallay.value.ValueType(ServerAgentEventPayload.ValueSchemaProvider.class)
public final class ServerAgentEventPayload {
    private final UUID requestId;
    private final String eventType;
    private final String eventJson;
    private final boolean terminal;
    public ServerAgentEventPayload(UUID requestId, String eventType, String eventJson, boolean terminal) {

        java.util.Objects.requireNonNull(requestId, "requestId");
        if (eventType == null || dev.openallay.util.Java8Strings.isBlank(eventType) || eventJson == null || dev.openallay.util.Java8Strings.isBlank(eventJson)) {
            throw new IllegalArgumentException("Agent event type and JSON are required");
        }

        this.requestId = requestId;
        this.eventType = eventType;
        this.eventJson = eventJson;
        this.terminal = terminal;
    }
    public UUID requestId() { return requestId; }
    public String eventType() { return eventType; }
    public String eventJson() { return eventJson; }
    public boolean terminal() { return terminal; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerAgentEventPayload)) return false;
        ServerAgentEventPayload that = (ServerAgentEventPayload) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(eventType, that.eventType) && java.util.Objects.equals(eventJson, that.eventJson) && terminal == that.terminal;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(eventType);
        hash = 31 * hash + java.util.Objects.hashCode(eventJson);
        hash = 31 * hash + Boolean.hashCode(terminal);
        return hash;
    }
    @Override public String toString() { return "ServerAgentEventPayload[requestId=" + requestId + ", eventType=" + eventType + ", eventJson=" + eventJson + ", terminal=" + terminal + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerAgentEventPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerAgentEventPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerAgentEventPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerAgentEventPayload.class, "requestId", ServerAgentEventPayload::requestId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentEventPayload.class, "eventType", ServerAgentEventPayload::eventType), new dev.openallay.value.ValueSchema.Component<>(ServerAgentEventPayload.class, "eventJson", ServerAgentEventPayload::eventJson), new dev.openallay.value.ValueSchema.Component<>(ServerAgentEventPayload.class, "terminal", ServerAgentEventPayload::terminal)), arguments -> new ServerAgentEventPayload((UUID) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3]));
        }
    }
}
