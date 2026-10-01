package dev.openallay.guide.history;

import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.model.ModelMessage;
import java.util.List;

/**
 * Current provider-neutral Agent transcript, independent of GUI pages.
 * It may exceed the requested input budget. Agent compaction owns that decision;
 * durable loading never silently drops actual calls, results, or failures.
 */
public record GuideHistoryContextSeed(
        String sessionId,
        List<ModelMessage> messages,
        List<ContextCheckpoint> checkpoints,
        int estimatedTokens) {
    public GuideHistoryContextSeed {
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid session ID");
        }
        messages = ModelContextCodec.safe(messages);
        checkpoints = List.copyOf(checkpoints);
        if (estimatedTokens < 0) {
            throw new IllegalArgumentException("context estimate must not be negative");
        }
    }
}
