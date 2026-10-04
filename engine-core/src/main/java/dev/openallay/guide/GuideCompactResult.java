package dev.openallay.guide;

import dev.openallay.agent.context.ContextCheckpoint;

/** Actual manual-control outcome. It is not a model answer or an Agent request. */
public record GuideCompactResult(Status status, int beforeTokens, int afterTokens,
        int inputBudget, ContextCheckpoint checkpoint) {
    public enum Status { COMPACTED, NOT_NEEDED }

    public GuideCompactResult {
        java.util.Objects.requireNonNull(status, "status");
        if (beforeTokens < 0 || afterTokens < 0 || inputBudget < 1) {
            throw new IllegalArgumentException("Invalid compaction budget");
        }
        if ((status == Status.COMPACTED) != (checkpoint != null)) {
            throw new IllegalArgumentException("Compaction outcome must match its actual checkpoint");
        }
    }
}
