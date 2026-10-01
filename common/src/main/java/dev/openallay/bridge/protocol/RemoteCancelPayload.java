package dev.openallay.bridge.protocol;

import java.util.UUID;

public record RemoteCancelPayload(UUID correlationId) {
    public RemoteCancelPayload {
        java.util.Objects.requireNonNull(correlationId, "correlationId");
    }
}
