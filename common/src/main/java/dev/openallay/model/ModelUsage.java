package dev.openallay.model;

/** Canonical provider counts. Input includes cache reads and writes; absent counts stay unknown. */
public record ModelUsage(
        long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens,
        long uncachedInputTokens, boolean inputKnown, boolean outputKnown,
        boolean cacheReadKnown, boolean cacheWriteKnown, boolean uncachedInputKnown) {
    public ModelUsage {
        if (inputTokens < 0 || outputTokens < 0 || cacheReadTokens < 0
                || cacheWriteTokens < 0 || uncachedInputTokens < 0) {
            throw new IllegalArgumentException("Model usage values must be non-negative");
        }
        if (!outputKnown && outputTokens != 0 || !cacheReadKnown && cacheReadTokens != 0
                || !cacheWriteKnown && cacheWriteTokens != 0 || !uncachedInputKnown && uncachedInputTokens != 0) {
            throw new IllegalArgumentException("Unknown usage categories cannot carry reported counts");
        }
        long categorized = Math.addExact(Math.addExact(uncachedInputTokens, cacheReadTokens), cacheWriteTokens);
        if (inputKnown && categorized > inputTokens) {
            throw new IllegalArgumentException("Cache counts exceed total model input");
        }
        if (inputKnown && uncachedInputKnown && cacheReadKnown && cacheWriteKnown
                && categorized != inputTokens) {
            throw new IllegalArgumentException("Model input categories must sum to total input");
        }
    }

    /** Explicit canonical counts, including an explicitly reported zero cache read. */
    public ModelUsage(long inputTokens, long outputTokens, long cacheReadTokens) {
        this(inputTokens, outputTokens, cacheReadTokens, 0,
                Math.subtractExact(inputTokens, cacheReadTokens), true, true, true, true, true);
    }

    public static ModelUsage openAi(
            long input, boolean inputKnown, long output, boolean outputKnown,
            long cacheRead, boolean cacheReadKnown) {
        return new ModelUsage(input, output, cacheRead, 0,
                inputKnown && cacheReadKnown ? Math.subtractExact(input, cacheRead) : 0,
                inputKnown, outputKnown, cacheReadKnown, true, inputKnown && cacheReadKnown);
    }

    public static ModelUsage anthropic(
            long uncached, boolean uncachedKnown, long output, boolean outputKnown,
            long cacheRead, boolean cacheReadKnown, long cacheWrite, boolean cacheWriteKnown) {
        return new ModelUsage(Math.addExact(Math.addExact(uncached, cacheRead), cacheWrite),
                output, cacheRead, cacheWrite, uncached,
                uncachedKnown && cacheReadKnown && cacheWriteKnown, outputKnown,
                cacheReadKnown, cacheWriteKnown, uncachedKnown);
    }

    /** No provider report. This is not a known zero-token call. */
    public static ModelUsage empty() {
        return new ModelUsage(0, 0, 0, 0, 0, false, false, false, false, false);
    }

    public boolean reported() {
        return inputKnown || outputKnown || uncachedInputKnown || cacheReadKnown || cacheWriteTokens > 0;
    }

    public boolean complete() {
        return inputKnown && outputKnown && cacheReadKnown && cacheWriteKnown && uncachedInputKnown;
    }
}
