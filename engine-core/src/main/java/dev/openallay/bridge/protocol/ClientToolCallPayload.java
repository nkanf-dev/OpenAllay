package dev.openallay.bridge.protocol;

import java.util.UUID;

/** One server-hosted Agent invocation bound to the requesting player's client. */
@dev.openallay.value.ValueType(ClientToolCallPayload.ValueSchemaProvider.class)
public final class ClientToolCallPayload {
    private final UUID requestId;
    private final UUID invocationId;
    private final String sessionId;
    private final String toolId;
    private final String argumentsJson;
    public ClientToolCallPayload(UUID requestId, UUID invocationId, String sessionId, String toolId, String argumentsJson) {

        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(invocationId, "invocationId");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid Agent session ID");
        }
        if (toolId == null || !toolId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid client Tool ID");
        }
        if (argumentsJson == null || dev.openallay.util.Java8Strings.isBlank(argumentsJson)) {
            throw new IllegalArgumentException("Client Tool arguments are required");
        }

        this.requestId = requestId;
        this.invocationId = invocationId;
        this.sessionId = sessionId;
        this.toolId = toolId;
        this.argumentsJson = argumentsJson;
    }
    public UUID requestId() { return requestId; }
    public UUID invocationId() { return invocationId; }
    public String sessionId() { return sessionId; }
    public String toolId() { return toolId; }
    public String argumentsJson() { return argumentsJson; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClientToolCallPayload)) return false;
        ClientToolCallPayload that = (ClientToolCallPayload) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(invocationId, that.invocationId) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(toolId, that.toolId) && java.util.Objects.equals(argumentsJson, that.argumentsJson);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(invocationId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        hash = 31 * hash + java.util.Objects.hashCode(argumentsJson);
        return hash;
    }
    @Override public String toString() { return "ClientToolCallPayload[requestId=" + requestId + ", invocationId=" + invocationId + ", sessionId=" + sessionId + ", toolId=" + toolId + ", argumentsJson=" + argumentsJson + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ClientToolCallPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(ClientToolCallPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ClientToolCallPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(ClientToolCallPayload.class, "requestId", ClientToolCallPayload::requestId), new dev.openallay.value.ValueSchema.Component<>(ClientToolCallPayload.class, "invocationId", ClientToolCallPayload::invocationId), new dev.openallay.value.ValueSchema.Component<>(ClientToolCallPayload.class, "sessionId", ClientToolCallPayload::sessionId), new dev.openallay.value.ValueSchema.Component<>(ClientToolCallPayload.class, "toolId", ClientToolCallPayload::toolId), new dev.openallay.value.ValueSchema.Component<>(ClientToolCallPayload.class, "argumentsJson", ClientToolCallPayload::argumentsJson)), arguments -> new ClientToolCallPayload((UUID) arguments[0], (UUID) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4]));
        }
    }
}
