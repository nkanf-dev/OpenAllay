package dev.openallay.bridge.protocol;

import java.util.UUID;

/** Cancels one reverse client Tool invocation without cancelling another request. */
@dev.openallay.value.ValueType(ClientToolCancelPayload.ValueSchemaProvider.class)
public final class ClientToolCancelPayload {
    private final UUID requestId;
    private final UUID invocationId;
    public ClientToolCancelPayload(UUID requestId, UUID invocationId) {

        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(invocationId, "invocationId");

        this.requestId = requestId;
        this.invocationId = invocationId;
    }
    public UUID requestId() { return requestId; }
    public UUID invocationId() { return invocationId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClientToolCancelPayload)) return false;
        ClientToolCancelPayload that = (ClientToolCancelPayload) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(invocationId, that.invocationId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(invocationId);
        return hash;
    }
    @Override public String toString() { return "ClientToolCancelPayload[requestId=" + requestId + ", invocationId=" + invocationId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ClientToolCancelPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(ClientToolCancelPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ClientToolCancelPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(ClientToolCancelPayload.class, "requestId", ClientToolCancelPayload::requestId), new dev.openallay.value.ValueSchema.Component<>(ClientToolCancelPayload.class, "invocationId", ClientToolCancelPayload::invocationId)), arguments -> new ClientToolCancelPayload((UUID) arguments[0], (UUID) arguments[1]));
        }
    }
}
