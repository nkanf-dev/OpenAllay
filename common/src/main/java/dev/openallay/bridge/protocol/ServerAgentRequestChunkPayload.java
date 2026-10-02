package dev.openallay.bridge.protocol;

import java.util.UUID;

public record ServerAgentRequestChunkPayload(
        UUID requestId,
        int index,
        int total,
        String contentHash,
        String base64Data) {
    public ServerAgentRequestChunkPayload {
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
    }
}
