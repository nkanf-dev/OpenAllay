package dev.openallay.bridge.protocol;

import java.util.UUID;

public record ServerAgentEventPayload(
        UUID requestId, String eventType, String eventJson, boolean terminal) {
    public ServerAgentEventPayload {
        java.util.Objects.requireNonNull(requestId, "requestId");
        if (eventType == null || eventType.isBlank() || eventJson == null || eventJson.isBlank()) {
            throw new IllegalArgumentException("Agent event type and JSON are required");
        }
    }
}
