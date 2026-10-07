package dev.openallay.bridge.protocol;

import java.util.UUID;

@dev.openallay.value.ValueType(RemoteToolResultChunkPayload.ValueSchemaProvider.class)
public final class RemoteToolResultChunkPayload {
    private final UUID correlationId;
    private final int index;
    private final int total;
    private final String contentHash;
    private final String base64Data;
    public RemoteToolResultChunkPayload(UUID correlationId, int index, int total, String contentHash, String base64Data) {

        java.util.Objects.requireNonNull(correlationId, "correlationId");
        if (index < 0 || total <= 0 || index >= total) {
            throw new IllegalArgumentException("Invalid result chunk position");
        }
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid SHA-256 content hash");
        }
        if (base64Data == null) {
            throw new IllegalArgumentException("Chunk data is required");
        }
        try {
            java.util.Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Chunk data is not valid base64", failure);
        }

        this.correlationId = correlationId;
        this.index = index;
        this.total = total;
        this.contentHash = contentHash;
        this.base64Data = base64Data;
    }
    public UUID correlationId() { return correlationId; }
    public int index() { return index; }
    public int total() { return total; }
    public String contentHash() { return contentHash; }
    public String base64Data() { return base64Data; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RemoteToolResultChunkPayload)) return false;
        RemoteToolResultChunkPayload that = (RemoteToolResultChunkPayload) other;
        return java.util.Objects.equals(correlationId, that.correlationId) && index == that.index && total == that.total && java.util.Objects.equals(contentHash, that.contentHash) && java.util.Objects.equals(base64Data, that.base64Data);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(correlationId);
        hash = 31 * hash + Integer.hashCode(index);
        hash = 31 * hash + Integer.hashCode(total);
        hash = 31 * hash + java.util.Objects.hashCode(contentHash);
        hash = 31 * hash + java.util.Objects.hashCode(base64Data);
        return hash;
    }
    @Override public String toString() { return "RemoteToolResultChunkPayload[correlationId=" + correlationId + ", index=" + index + ", total=" + total + ", contentHash=" + contentHash + ", base64Data=" + base64Data + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RemoteToolResultChunkPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(RemoteToolResultChunkPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RemoteToolResultChunkPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(RemoteToolResultChunkPayload.class, "correlationId", RemoteToolResultChunkPayload::correlationId), new dev.openallay.value.ValueSchema.Component<>(RemoteToolResultChunkPayload.class, "index", RemoteToolResultChunkPayload::index), new dev.openallay.value.ValueSchema.Component<>(RemoteToolResultChunkPayload.class, "total", RemoteToolResultChunkPayload::total), new dev.openallay.value.ValueSchema.Component<>(RemoteToolResultChunkPayload.class, "contentHash", RemoteToolResultChunkPayload::contentHash), new dev.openallay.value.ValueSchema.Component<>(RemoteToolResultChunkPayload.class, "base64Data", RemoteToolResultChunkPayload::base64Data)), arguments -> new RemoteToolResultChunkPayload((UUID) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (String) arguments[3], (String) arguments[4]));
        }
    }
}
