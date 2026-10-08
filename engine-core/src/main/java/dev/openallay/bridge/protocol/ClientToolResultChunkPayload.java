package dev.openallay.bridge.protocol;

import java.util.UUID;

/** One hash-checked chunk of a normalized player-client Tool result. */
@dev.openallay.value.ValueType(ClientToolResultChunkPayload.ValueSchemaProvider.class)
public final class ClientToolResultChunkPayload {
    private final UUID requestId;
    private final UUID invocationId;
    private final int index;
    private final int total;
    private final String contentHash;
    private final String base64Data;
    public ClientToolResultChunkPayload(UUID requestId, UUID invocationId, int index, int total, String contentHash, String base64Data) {

        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(invocationId, "invocationId");
        if (index < 0 || total <= 0 || index >= total
                || total > BridgeProtocol.MAX_REQUEST_CHUNKS) {
            throw new IllegalArgumentException("Invalid client Tool result chunk position");
        }
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid client Tool result SHA-256 hash");
        }
        if (base64Data == null
                || base64Data.length() > BridgeProtocol.MAX_REQUEST_CHUNK_BASE64_CHARS) {
            throw new IllegalArgumentException("Client Tool result chunk exceeds transport limits");
        }
        try {
            if (java.util.Base64.getDecoder().decode(base64Data).length
                    > BridgeProtocol.TRANSPORT_CHUNK_BYTES) {
                throw new IllegalArgumentException("Client Tool result raw chunk exceeds transport limits");
            }
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Client Tool result chunk is not valid base64", failure);
        }

        this.requestId = requestId;
        this.invocationId = invocationId;
        this.index = index;
        this.total = total;
        this.contentHash = contentHash;
        this.base64Data = base64Data;
    }
    public UUID requestId() { return requestId; }
    public UUID invocationId() { return invocationId; }
    public int index() { return index; }
    public int total() { return total; }
    public String contentHash() { return contentHash; }
    public String base64Data() { return base64Data; }
public RemoteToolResultChunkPayload asRemoteChunk() {
        return new RemoteToolResultChunkPayload(
                invocationId, index, total, contentHash, base64Data);
    }
public static ClientToolResultChunkPayload from(
            UUID requestId, RemoteToolResultChunkPayload chunk) {
        return new ClientToolResultChunkPayload(
                requestId, chunk.correlationId(), chunk.index(), chunk.total(),
                chunk.contentHash(), chunk.base64Data());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClientToolResultChunkPayload)) return false;
        ClientToolResultChunkPayload that = (ClientToolResultChunkPayload) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(invocationId, that.invocationId) && index == that.index && total == that.total && java.util.Objects.equals(contentHash, that.contentHash) && java.util.Objects.equals(base64Data, that.base64Data);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(invocationId);
        hash = 31 * hash + Integer.hashCode(index);
        hash = 31 * hash + Integer.hashCode(total);
        hash = 31 * hash + java.util.Objects.hashCode(contentHash);
        hash = 31 * hash + java.util.Objects.hashCode(base64Data);
        return hash;
    }
    @Override public String toString() { return "ClientToolResultChunkPayload[requestId=" + requestId + ", invocationId=" + invocationId + ", index=" + index + ", total=" + total + ", contentHash=" + contentHash + ", base64Data=" + base64Data + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ClientToolResultChunkPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(ClientToolResultChunkPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ClientToolResultChunkPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(ClientToolResultChunkPayload.class, "requestId", ClientToolResultChunkPayload::requestId), new dev.openallay.value.ValueSchema.Component<>(ClientToolResultChunkPayload.class, "invocationId", ClientToolResultChunkPayload::invocationId), new dev.openallay.value.ValueSchema.Component<>(ClientToolResultChunkPayload.class, "index", ClientToolResultChunkPayload::index), new dev.openallay.value.ValueSchema.Component<>(ClientToolResultChunkPayload.class, "total", ClientToolResultChunkPayload::total), new dev.openallay.value.ValueSchema.Component<>(ClientToolResultChunkPayload.class, "contentHash", ClientToolResultChunkPayload::contentHash), new dev.openallay.value.ValueSchema.Component<>(ClientToolResultChunkPayload.class, "base64Data", ClientToolResultChunkPayload::base64Data)), arguments -> new ClientToolResultChunkPayload((UUID) arguments[0], (UUID) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (String) arguments[4], (String) arguments[5]));
        }
    }
}
