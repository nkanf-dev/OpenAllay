package dev.openallay.bridge.protocol;

import java.util.Objects;
import java.util.UUID;

/** A transport chunk of one request-correlated inbox edit. */
@dev.openallay.value.ValueType(ServerAgentSteerChunkPayload.ValueSchemaProvider.class)
public final class ServerAgentSteerChunkPayload {
    private final UUID requestId;
    private final UUID messageId;
    private final int index;
    private final int total;
    private final String contentHash;
    private final String base64Data;
    public ServerAgentSteerChunkPayload(UUID requestId, UUID messageId, int index, int total, String contentHash, String base64Data) {

        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(messageId, "messageId");
        // Reuse the common transport-value validation without merging request ownership.
        new ServerAgentRequestChunkPayload(messageId, index, total, contentHash, base64Data);

        this.requestId = requestId;
        this.messageId = messageId;
        this.index = index;
        this.total = total;
        this.contentHash = contentHash;
        this.base64Data = base64Data;
    }
    public UUID requestId() { return requestId; }
    public UUID messageId() { return messageId; }
    public int index() { return index; }
    public int total() { return total; }
    public String contentHash() { return contentHash; }
    public String base64Data() { return base64Data; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerAgentSteerChunkPayload)) return false;
        ServerAgentSteerChunkPayload that = (ServerAgentSteerChunkPayload) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(messageId, that.messageId) && index == that.index && total == that.total && java.util.Objects.equals(contentHash, that.contentHash) && java.util.Objects.equals(base64Data, that.base64Data);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        hash = 31 * hash + Integer.hashCode(index);
        hash = 31 * hash + Integer.hashCode(total);
        hash = 31 * hash + java.util.Objects.hashCode(contentHash);
        hash = 31 * hash + java.util.Objects.hashCode(base64Data);
        return hash;
    }
    @Override public String toString() { return "ServerAgentSteerChunkPayload[requestId=" + requestId + ", messageId=" + messageId + ", index=" + index + ", total=" + total + ", contentHash=" + contentHash + ", base64Data=" + base64Data + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerAgentSteerChunkPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerAgentSteerChunkPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerAgentSteerChunkPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerChunkPayload.class, "requestId", ServerAgentSteerChunkPayload::requestId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerChunkPayload.class, "messageId", ServerAgentSteerChunkPayload::messageId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerChunkPayload.class, "index", ServerAgentSteerChunkPayload::index), new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerChunkPayload.class, "total", ServerAgentSteerChunkPayload::total), new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerChunkPayload.class, "contentHash", ServerAgentSteerChunkPayload::contentHash), new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerChunkPayload.class, "base64Data", ServerAgentSteerChunkPayload::base64Data)), arguments -> new ServerAgentSteerChunkPayload((UUID) arguments[0], (UUID) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (String) arguments[4], (String) arguments[5]));
        }
    }
}
