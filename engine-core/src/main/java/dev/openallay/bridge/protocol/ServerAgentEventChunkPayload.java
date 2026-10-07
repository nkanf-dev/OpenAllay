package dev.openallay.bridge.protocol;

import java.util.UUID;

/** One hash-checked transport chunk of a complete server Agent event payload. */
@dev.openallay.value.ValueType(ServerAgentEventChunkPayload.ValueSchemaProvider.class)
public final class ServerAgentEventChunkPayload {
    private final UUID requestId;
    private final UUID eventId;
    private final int index;
    private final int total;
    private final String contentHash;
    private final String base64Data;
    public ServerAgentEventChunkPayload(UUID requestId, UUID eventId, int index, int total, String contentHash, String base64Data) {

        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(eventId, "eventId");
        if (index < 0 || total <= 0 || index >= total) {
            throw new IllegalArgumentException("Invalid server Agent event chunk position");
        }
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid server Agent event SHA-256 hash");
        }
        if (base64Data == null) {
            throw new IllegalArgumentException("Server Agent event chunk data is required");
        }
        try {
            java.util.Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "Server Agent event chunk is not valid base64", failure);
        }

        this.requestId = requestId;
        this.eventId = eventId;
        this.index = index;
        this.total = total;
        this.contentHash = contentHash;
        this.base64Data = base64Data;
    }
    public UUID requestId() { return requestId; }
    public UUID eventId() { return eventId; }
    public int index() { return index; }
    public int total() { return total; }
    public String contentHash() { return contentHash; }
    public String base64Data() { return base64Data; }
public RemoteToolResultChunkPayload asRemoteChunk() {
        return new RemoteToolResultChunkPayload(
                eventId, index, total, contentHash, base64Data);
    }
public static ServerAgentEventChunkPayload from(
            UUID requestId, RemoteToolResultChunkPayload chunk) {
        return new ServerAgentEventChunkPayload(
                requestId, chunk.correlationId(), chunk.index(), chunk.total(),
                chunk.contentHash(), chunk.base64Data());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerAgentEventChunkPayload)) return false;
        ServerAgentEventChunkPayload that = (ServerAgentEventChunkPayload) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(eventId, that.eventId) && index == that.index && total == that.total && java.util.Objects.equals(contentHash, that.contentHash) && java.util.Objects.equals(base64Data, that.base64Data);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(eventId);
        hash = 31 * hash + Integer.hashCode(index);
        hash = 31 * hash + Integer.hashCode(total);
        hash = 31 * hash + java.util.Objects.hashCode(contentHash);
        hash = 31 * hash + java.util.Objects.hashCode(base64Data);
        return hash;
    }
    @Override public String toString() { return "ServerAgentEventChunkPayload[requestId=" + requestId + ", eventId=" + eventId + ", index=" + index + ", total=" + total + ", contentHash=" + contentHash + ", base64Data=" + base64Data + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerAgentEventChunkPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerAgentEventChunkPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerAgentEventChunkPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerAgentEventChunkPayload.class, "requestId", ServerAgentEventChunkPayload::requestId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentEventChunkPayload.class, "eventId", ServerAgentEventChunkPayload::eventId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentEventChunkPayload.class, "index", ServerAgentEventChunkPayload::index), new dev.openallay.value.ValueSchema.Component<>(ServerAgentEventChunkPayload.class, "total", ServerAgentEventChunkPayload::total), new dev.openallay.value.ValueSchema.Component<>(ServerAgentEventChunkPayload.class, "contentHash", ServerAgentEventChunkPayload::contentHash), new dev.openallay.value.ValueSchema.Component<>(ServerAgentEventChunkPayload.class, "base64Data", ServerAgentEventChunkPayload::base64Data)), arguments -> new ServerAgentEventChunkPayload((UUID) arguments[0], (UUID) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (String) arguments[4], (String) arguments[5]));
        }
    }
}
