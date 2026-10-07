package dev.openallay.bridge.protocol;

import java.util.UUID;

@dev.openallay.value.ValueType(ServerAgentRequestChunkPayload.ValueSchemaProvider.class)
public final class ServerAgentRequestChunkPayload {
    private final UUID requestId;
    private final int index;
    private final int total;
    private final String contentHash;
    private final String base64Data;
    public ServerAgentRequestChunkPayload(UUID requestId, int index, int total, String contentHash, String base64Data) {

        java.util.Objects.requireNonNull(requestId, "requestId");
        if (index < 0 || total <= 0 || total > BridgeProtocol.MAX_REQUEST_CHUNKS || index >= total) {
            throw new IllegalArgumentException("Invalid server Agent request chunk position");
        }
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid SHA-256 content hash");
        }
        if (base64Data == null || base64Data.length() > BridgeProtocol.MAX_REQUEST_CHUNK_BASE64_CHARS) {
            throw new IllegalArgumentException("Chunk data exceeds its transport envelope");
        }
        if (base64Data.length() % 4 != 0) {
            throw new IllegalArgumentException("Chunk data must be canonical base64");
        }
        int padding = base64Data.endsWith("==") ? 2 : base64Data.endsWith("=") ? 1 : 0;
        long decodedBytes = (base64Data.length() * 3L) / 4 - padding;
        if (decodedBytes < 0 || decodedBytes > BridgeProtocol.TRANSPORT_CHUNK_BYTES) {
            throw new IllegalArgumentException("Chunk data exceeds its transport envelope");
        }
        int dataEnd = base64Data.length() - padding;
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int offset = 0; offset < base64Data.length(); offset++) {
            char value = base64Data.charAt(offset);
            if (offset >= dataEnd ? value != '=' : alphabet.indexOf(value) < 0) {
                throw new IllegalArgumentException("Chunk data must be canonical base64");
            }
        }
        if (padding > 0 && (dataEnd == 0
                || (alphabet.indexOf(base64Data.charAt(dataEnd - 1)) & (padding == 2 ? 15 : 3)) != 0)) {
            throw new IllegalArgumentException("Chunk data must be canonical base64");
        }

        this.requestId = requestId;
        this.index = index;
        this.total = total;
        this.contentHash = contentHash;
        this.base64Data = base64Data;
    }
    public UUID requestId() { return requestId; }
    public int index() { return index; }
    public int total() { return total; }
    public String contentHash() { return contentHash; }
    public String base64Data() { return base64Data; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerAgentRequestChunkPayload)) return false;
        ServerAgentRequestChunkPayload that = (ServerAgentRequestChunkPayload) other;
        return java.util.Objects.equals(requestId, that.requestId) && index == that.index && total == that.total && java.util.Objects.equals(contentHash, that.contentHash) && java.util.Objects.equals(base64Data, that.base64Data);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + Integer.hashCode(index);
        hash = 31 * hash + Integer.hashCode(total);
        hash = 31 * hash + java.util.Objects.hashCode(contentHash);
        hash = 31 * hash + java.util.Objects.hashCode(base64Data);
        return hash;
    }
    @Override public String toString() { return "ServerAgentRequestChunkPayload[requestId=" + requestId + ", index=" + index + ", total=" + total + ", contentHash=" + contentHash + ", base64Data=" + base64Data + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerAgentRequestChunkPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerAgentRequestChunkPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerAgentRequestChunkPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestChunkPayload.class, "requestId", ServerAgentRequestChunkPayload::requestId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestChunkPayload.class, "index", ServerAgentRequestChunkPayload::index), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestChunkPayload.class, "total", ServerAgentRequestChunkPayload::total), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestChunkPayload.class, "contentHash", ServerAgentRequestChunkPayload::contentHash), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestChunkPayload.class, "base64Data", ServerAgentRequestChunkPayload::base64Data)), arguments -> new ServerAgentRequestChunkPayload((UUID) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (String) arguments[3], (String) arguments[4]));
        }
    }
}
