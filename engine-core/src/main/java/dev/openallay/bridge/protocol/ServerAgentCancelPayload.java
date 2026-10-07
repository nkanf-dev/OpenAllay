package dev.openallay.bridge.protocol;

import java.util.UUID;

@dev.openallay.value.ValueType(ServerAgentCancelPayload.ValueSchemaProvider.class)
public final class ServerAgentCancelPayload {
    private final UUID requestId;
    public ServerAgentCancelPayload(UUID requestId) {

        java.util.Objects.requireNonNull(requestId, "requestId");

        this.requestId = requestId;
    }
    public UUID requestId() { return requestId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerAgentCancelPayload)) return false;
        ServerAgentCancelPayload that = (ServerAgentCancelPayload) other;
        return java.util.Objects.equals(requestId, that.requestId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        return hash;
    }
    @Override public String toString() { return "ServerAgentCancelPayload[requestId=" + requestId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerAgentCancelPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerAgentCancelPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerAgentCancelPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerAgentCancelPayload.class, "requestId", ServerAgentCancelPayload::requestId)), arguments -> new ServerAgentCancelPayload((UUID) arguments[0]));
        }
    }
}
