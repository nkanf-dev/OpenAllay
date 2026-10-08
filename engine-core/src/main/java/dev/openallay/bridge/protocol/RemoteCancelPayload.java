package dev.openallay.bridge.protocol;

import java.util.UUID;

@dev.openallay.value.ValueType(RemoteCancelPayload.ValueSchemaProvider.class)
public final class RemoteCancelPayload {
    private final UUID correlationId;
    public RemoteCancelPayload(UUID correlationId) {

        java.util.Objects.requireNonNull(correlationId, "correlationId");

        this.correlationId = correlationId;
    }
    public UUID correlationId() { return correlationId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RemoteCancelPayload)) return false;
        RemoteCancelPayload that = (RemoteCancelPayload) other;
        return java.util.Objects.equals(correlationId, that.correlationId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(correlationId);
        return hash;
    }
    @Override public String toString() { return "RemoteCancelPayload[correlationId=" + correlationId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RemoteCancelPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(RemoteCancelPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RemoteCancelPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(RemoteCancelPayload.class, "correlationId", RemoteCancelPayload::correlationId)), arguments -> new RemoteCancelPayload((UUID) arguments[0]));
        }
    }
}
