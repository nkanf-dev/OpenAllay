package dev.openallay.bridge.protocol;

import java.util.UUID;

public record ServerAgentCancelPayload(UUID requestId) {
    public ServerAgentCancelPayload {
        java.util.Objects.requireNonNull(requestId, "requestId");
    }
}
