package dev.openallay.bridge.protocol;

import java.util.UUID;

/** Cancels one reverse client Tool invocation without cancelling another request. */
public record ClientToolCancelPayload(UUID requestId, UUID invocationId) {
    public ClientToolCancelPayload {
        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(invocationId, "invocationId");
    }
}
