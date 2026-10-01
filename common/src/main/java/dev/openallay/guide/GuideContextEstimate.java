package dev.openallay.guide;

import java.util.Objects;
import java.util.UUID;

/** Runtime-only estimate of the latest submitted model input, including prompt and tools. */
public record GuideContextEstimate(
        UUID requestId, long estimatedTokens,
        dev.openallay.agent.context.ContextBudget budget, String modelIdentifier) {
    public GuideContextEstimate {
        Objects.requireNonNull(requestId, "requestId");
        if (estimatedTokens < 0) throw new IllegalArgumentException("Estimate must not be negative");
        if ((budget == null) != (modelIdentifier == null)
                || modelIdentifier != null && modelIdentifier.isBlank()) {
            throw new IllegalArgumentException("Captured budget and model identity must be supplied together");
        }
    }

    public GuideContextEstimate(UUID requestId, long estimatedTokens) {
        this(requestId, estimatedTokens, null, null);
    }
}
