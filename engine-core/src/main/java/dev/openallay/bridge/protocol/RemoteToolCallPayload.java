package dev.openallay.bridge.protocol;

import java.util.UUID;

@dev.openallay.value.ValueType(RemoteToolCallPayload.ValueSchemaProvider.class)
public final class RemoteToolCallPayload {
    private final UUID correlationId;
    private final String sessionId;
    private final String toolId;
    private final String argumentsJson;
    public RemoteToolCallPayload(UUID correlationId, String sessionId, String toolId, String argumentsJson) {

        java.util.Objects.requireNonNull(correlationId, "correlationId");
        require(sessionId, "sessionId");
        require(toolId, "toolId");
        require(argumentsJson, "argumentsJson");

        this.correlationId = correlationId;
        this.sessionId = sessionId;
        this.toolId = toolId;
        this.argumentsJson = argumentsJson;
    }
    public UUID correlationId() { return correlationId; }
    public String sessionId() { return sessionId; }
    public String toolId() { return toolId; }
    public String argumentsJson() { return argumentsJson; }
private static void require(String value, String name) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RemoteToolCallPayload)) return false;
        RemoteToolCallPayload that = (RemoteToolCallPayload) other;
        return java.util.Objects.equals(correlationId, that.correlationId) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(toolId, that.toolId) && java.util.Objects.equals(argumentsJson, that.argumentsJson);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(correlationId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        hash = 31 * hash + java.util.Objects.hashCode(argumentsJson);
        return hash;
    }
    @Override public String toString() { return "RemoteToolCallPayload[correlationId=" + correlationId + ", sessionId=" + sessionId + ", toolId=" + toolId + ", argumentsJson=" + argumentsJson + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RemoteToolCallPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(RemoteToolCallPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RemoteToolCallPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(RemoteToolCallPayload.class, "correlationId", RemoteToolCallPayload::correlationId), new dev.openallay.value.ValueSchema.Component<>(RemoteToolCallPayload.class, "sessionId", RemoteToolCallPayload::sessionId), new dev.openallay.value.ValueSchema.Component<>(RemoteToolCallPayload.class, "toolId", RemoteToolCallPayload::toolId), new dev.openallay.value.ValueSchema.Component<>(RemoteToolCallPayload.class, "argumentsJson", RemoteToolCallPayload::argumentsJson)), arguments -> new RemoteToolCallPayload((UUID) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}
