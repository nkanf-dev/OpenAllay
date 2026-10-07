package dev.openallay.agent.context;

import dev.openallay.model.ModelMessage;
import java.util.List;
import java.util.Objects;

@dev.openallay.value.ValueType(ContextProjection.ValueSchemaProvider.class)
public final class ContextProjection {
    private final List<ModelMessage> messages;
    private final Kind kind;
    private final int estimatedTokens;
    public ContextProjection(List<ModelMessage> messages, Kind kind, int estimatedTokens) {

        messages = dev.openallay.util.Java8Collections.listCopyOf(messages);
        Objects.requireNonNull(kind, "kind");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("context projection must contain messages");
        }
        if (estimatedTokens < -1) {
            throw new IllegalArgumentException("estimatedTokens must be -1 or non-negative");
        }

        this.messages = messages;
        this.kind = kind;
        this.estimatedTokens = estimatedTokens;
    }
    public List<ModelMessage> messages() { return messages; }
    public Kind kind() { return kind; }
    public int estimatedTokens() { return estimatedTokens; }
public enum Kind {
        ORIGINAL,
        BOUNDED,
        SUMMARIZED
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ContextProjection)) return false;
        ContextProjection that = (ContextProjection) other;
        return java.util.Objects.equals(messages, that.messages) && java.util.Objects.equals(kind, that.kind) && estimatedTokens == that.estimatedTokens;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + Integer.hashCode(estimatedTokens);
        return hash;
    }
    @Override public String toString() { return "ContextProjection[messages=" + messages + ", kind=" + kind + ", estimatedTokens=" + estimatedTokens + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ContextProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(ContextProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ContextProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(ContextProjection.class, "messages", ContextProjection::messages), new dev.openallay.value.ValueSchema.Component<>(ContextProjection.class, "kind", ContextProjection::kind), new dev.openallay.value.ValueSchema.Component<>(ContextProjection.class, "estimatedTokens", ContextProjection::estimatedTokens)), arguments -> new ContextProjection((List) arguments[0], (Kind) arguments[1], (Integer) arguments[2]));
        }
    }
}
