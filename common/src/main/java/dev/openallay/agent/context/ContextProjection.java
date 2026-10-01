package dev.openallay.agent.context;

import dev.openallay.model.ModelMessage;
import java.util.List;
import java.util.Objects;

public record ContextProjection(List<ModelMessage> messages, Kind kind, int estimatedTokens) {
    public enum Kind {
        ORIGINAL,
        BOUNDED,
        SUMMARIZED
    }

    public ContextProjection {
        messages = List.copyOf(messages);
        Objects.requireNonNull(kind, "kind");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("context projection must contain messages");
        }
        if (estimatedTokens < -1) {
            throw new IllegalArgumentException("estimatedTokens must be -1 or non-negative");
        }
    }

}
