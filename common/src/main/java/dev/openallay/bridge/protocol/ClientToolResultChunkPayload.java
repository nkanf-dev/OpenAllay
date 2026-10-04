package dev.openallay.bridge.protocol;

import java.util.UUID;

/** One hash-checked chunk of a normalized player-client Tool result. */
public record ClientToolResultChunkPayload(
        UUID requestId,
        UUID invocationId,
        int index,
        int total,
        String contentHash,
        String base64Data) {
    public ClientToolResultChunkPayload {
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
    }

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
}
