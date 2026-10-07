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
@dev.openallay.value.ValueType(GuideHistoryContextSeed.ValueSchemaProvider.class)
public final class GuideHistoryContextSeed {
    private final String sessionId;
    private final List<ModelMessage> messages;
    private final List<ContextCheckpoint> checkpoints;
    private final int estimatedTokens;
    public GuideHistoryContextSeed(String sessionId, List<ModelMessage> messages, List<ContextCheckpoint> checkpoints, int estimatedTokens) {

        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid session ID");
        }
        messages = ModelContextCodec.safe(messages);
        checkpoints = dev.openallay.util.Java8Collections.listCopyOf(checkpoints);
        if (estimatedTokens < 0) {
            throw new IllegalArgumentException("context estimate must not be negative");
        }

        this.sessionId = sessionId;
        this.messages = messages;
        this.checkpoints = checkpoints;
        this.estimatedTokens = estimatedTokens;
    }
    public String sessionId() { return sessionId; }
    public List<ModelMessage> messages() { return messages; }
    public List<ContextCheckpoint> checkpoints() { return checkpoints; }
    public int estimatedTokens() { return estimatedTokens; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryContextSeed)) return false;
        GuideHistoryContextSeed that = (GuideHistoryContextSeed) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(messages, that.messages) && java.util.Objects.equals(checkpoints, that.checkpoints) && estimatedTokens == that.estimatedTokens;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + java.util.Objects.hashCode(checkpoints);
        hash = 31 * hash + Integer.hashCode(estimatedTokens);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryContextSeed[sessionId=" + sessionId + ", messages=" + messages + ", checkpoints=" + checkpoints + ", estimatedTokens=" + estimatedTokens + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryContextSeed> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryContextSeed.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryContextSeed>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryContextSeed.class, "sessionId", GuideHistoryContextSeed::sessionId), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryContextSeed.class, "messages", GuideHistoryContextSeed::messages), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryContextSeed.class, "checkpoints", GuideHistoryContextSeed::checkpoints), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryContextSeed.class, "estimatedTokens", GuideHistoryContextSeed::estimatedTokens)), arguments -> new GuideHistoryContextSeed((String) arguments[0], (List) arguments[1], (List) arguments[2], (Integer) arguments[3]));
        }
    }
}
