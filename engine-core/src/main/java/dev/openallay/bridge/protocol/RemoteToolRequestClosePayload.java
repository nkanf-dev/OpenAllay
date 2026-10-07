package dev.openallay.bridge.protocol;

/** Releases server-side request workspaces after a client-hosted Agent reaches a terminal state. */
@dev.openallay.value.ValueType(RemoteToolRequestClosePayload.ValueSchemaProvider.class)
public final class RemoteToolRequestClosePayload {
    private final String requestId;
    public RemoteToolRequestClosePayload(String requestId) {

        if (requestId == null || dev.openallay.util.Java8Strings.isBlank(requestId)) {
            throw new IllegalArgumentException("requestId is required");
        }

        this.requestId = requestId;
    }
    public String requestId() { return requestId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RemoteToolRequestClosePayload)) return false;
        RemoteToolRequestClosePayload that = (RemoteToolRequestClosePayload) other;
        return java.util.Objects.equals(requestId, that.requestId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        return hash;
    }
    @Override public String toString() { return "RemoteToolRequestClosePayload[requestId=" + requestId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RemoteToolRequestClosePayload> schema() {
            return new dev.openallay.value.ValueSchema<>(RemoteToolRequestClosePayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RemoteToolRequestClosePayload>>asList(new dev.openallay.value.ValueSchema.Component<>(RemoteToolRequestClosePayload.class, "requestId", RemoteToolRequestClosePayload::requestId)), arguments -> new RemoteToolRequestClosePayload((String) arguments[0]));
        }
    }
}
