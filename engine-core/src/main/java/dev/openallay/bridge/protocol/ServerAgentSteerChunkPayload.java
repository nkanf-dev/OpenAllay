package dev.openallay.bridge.protocol;

import java.util.Objects;
import java.util.UUID;

/** A transport chunk of one request-correlated inbox edit. */
public record ServerAgentSteerChunkPayload(
        UUID requestId,
        UUID messageId,
        int index,
        int total,
        String contentHash,
        String base64Data) {
    public ServerAgentSteerChunkPayload {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(messageId, "messageId");
        // Reuse the common transport-value validation without merging request ownership.
        new ServerAgentRequestChunkPayload(messageId, index, total, contentHash, base64Data);
    }
}
