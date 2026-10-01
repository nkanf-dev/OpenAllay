package dev.openallay.guide;

import java.util.Objects;
import java.util.UUID;

/** Runtime-only estimate of the latest submitted model input, including prompt and tools. */
public record GuideContextEstimate(UUID requestId, long estimatedTokens) {
    public GuideContextEstimate {
        Objects.requireNonNull(requestId, "requestId");
        if (estimatedTokens < 0) throw new IllegalArgumentException("Estimate must not be negative");
    }
}
