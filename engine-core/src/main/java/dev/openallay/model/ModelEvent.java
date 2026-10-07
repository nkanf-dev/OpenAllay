package dev.openallay.model;

import com.google.gson.JsonObject;
import java.util.Objects;

public interface ModelEvent {
    /** Runtime admission replacement for the modern compiler's sealed subtype contract. */
    static ModelEvent requireKnown(ModelEvent event) {
        Objects.requireNonNull(event, "event");
        Class<?> type = event.getClass();
        if (type == TextDelta.class || type == ReasoningDelta.class || type == ToolUseComplete.class
                || type == UsageUpdate.class || type == UsageObserved.class || type == UsageStarted.class
                || type == AttemptStarted.class || type == ResponseStarted.class || type == RateLimited.class
                || type == MessageComplete.class || type == ModelFailure.class) return event;
        throw new IncompatibleClassChangeError("Unknown model event subtype");
    }

    @dev.openallay.value.ValueType(TextDelta.ValueSchemaProvider.class)
public static final class TextDelta implements ModelEvent {
    private final String text;
    public TextDelta(String text) {

            Objects.requireNonNull(text, "text");

        this.text = text;
    }
    public String text() { return text; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TextDelta)) return false;
        TextDelta that = (TextDelta) other;
        return java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "TextDelta[text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TextDelta> schema() {
            return new dev.openallay.value.ValueSchema<>(TextDelta.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TextDelta>>asList(new dev.openallay.value.ValueSchema.Component<>(TextDelta.class, "text", TextDelta::text)), arguments -> new TextDelta((String) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(ReasoningDelta.ValueSchemaProvider.class)
public static final class ReasoningDelta implements ModelEvent {
    private final String text;
    public ReasoningDelta(String text) {

            Objects.requireNonNull(text, "text");

        this.text = text;
    }
    public String text() { return text; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReasoningDelta)) return false;
        ReasoningDelta that = (ReasoningDelta) other;
        return java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "ReasoningDelta[text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ReasoningDelta> schema() {
            return new dev.openallay.value.ValueSchema<>(ReasoningDelta.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ReasoningDelta>>asList(new dev.openallay.value.ValueSchema.Component<>(ReasoningDelta.class, "text", ReasoningDelta::text)), arguments -> new ReasoningDelta((String) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(ToolUseComplete.ValueSchemaProvider.class)
public static final class ToolUseComplete implements ModelEvent {
    private final String id;
    private final String name;
    private final JsonObject input;
    public ToolUseComplete(String id, String name, JsonObject input) {

            input = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(input, "input"));

        this.id = id;
        this.name = name;
        this.input = input;
    }
    public String id() { return id; }
    public String name() { return name; }

        public JsonObject input() {
            return dev.openallay.json.JsonTrees.copy(input);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolUseComplete)) return false;
        ToolUseComplete that = (ToolUseComplete) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(input, that.input);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(input);
        return hash;
    }
    @Override public String toString() { return "ToolUseComplete[id=" + id + ", name=" + name + ", input=" + input + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolUseComplete> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolUseComplete.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolUseComplete>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolUseComplete.class, "id", ToolUseComplete::id), new dev.openallay.value.ValueSchema.Component<>(ToolUseComplete.class, "name", ToolUseComplete::name), new dev.openallay.value.ValueSchema.Component<>(ToolUseComplete.class, "input", ToolUseComplete::input)), arguments -> new ToolUseComplete((String) arguments[0], (String) arguments[1], (JsonObject) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(UsageUpdate.ValueSchemaProvider.class)
public static final class UsageUpdate implements ModelEvent {
    private final ModelUsage usage;
    public UsageUpdate(ModelUsage usage) {
        this.usage = usage;
    }
    public ModelUsage usage() { return usage; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UsageUpdate)) return false;
        UsageUpdate that = (UsageUpdate) other;
        return java.util.Objects.equals(usage, that.usage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(usage);
        return hash;
    }
    @Override public String toString() { return "UsageUpdate[usage=" + usage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UsageUpdate> schema() {
            return new dev.openallay.value.ValueSchema<>(UsageUpdate.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UsageUpdate>>asList(new dev.openallay.value.ValueSchema.Component<>(UsageUpdate.class, "usage", UsageUpdate::usage)), arguments -> new UsageUpdate((ModelUsage) arguments[0]));
        }
    }
}

    /** Numeric lifecycle boundary sharing its identity with the eventual usage receipt. */
    @dev.openallay.value.ValueType(UsageStarted.ValueSchemaProvider.class)
public static final class UsageStarted implements ModelEvent {
    private final java.util.UUID callId;
    private final String modelIdentifier;
    public UsageStarted(java.util.UUID callId, String modelIdentifier) {

            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(modelIdentifier, "modelIdentifier");

        this.callId = callId;
        this.modelIdentifier = modelIdentifier;
    }
    public java.util.UUID callId() { return callId; }
    public String modelIdentifier() { return modelIdentifier; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UsageStarted)) return false;
        UsageStarted that = (UsageStarted) other;
        return java.util.Objects.equals(callId, that.callId) && java.util.Objects.equals(modelIdentifier, that.modelIdentifier);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(callId);
        hash = 31 * hash + java.util.Objects.hashCode(modelIdentifier);
        return hash;
    }
    @Override public String toString() { return "UsageStarted[callId=" + callId + ", modelIdentifier=" + modelIdentifier + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UsageStarted> schema() {
            return new dev.openallay.value.ValueSchema<>(UsageStarted.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UsageStarted>>asList(new dev.openallay.value.ValueSchema.Component<>(UsageStarted.class, "callId", UsageStarted::callId), new dev.openallay.value.ValueSchema.Component<>(UsageStarted.class, "modelIdentifier", UsageStarted::modelIdentifier)), arguments -> new UsageStarted((java.util.UUID) arguments[0], (String) arguments[1]));
        }
    }
}

    /** Counts-only receipt for one actual provider attempt, not a streamed usage delta. */
    @dev.openallay.value.ValueType(UsageObserved.ValueSchemaProvider.class)
public static final class UsageObserved implements ModelEvent {
    private final java.util.UUID callId;
    private final String modelIdentifier;
    private final ModelUsage usage;
    public UsageObserved(java.util.UUID callId, String modelIdentifier, ModelUsage usage) {

            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(modelIdentifier, "modelIdentifier");
            Objects.requireNonNull(usage, "usage");

        this.callId = callId;
        this.modelIdentifier = modelIdentifier;
        this.usage = usage;
    }
    public java.util.UUID callId() { return callId; }
    public String modelIdentifier() { return modelIdentifier; }
    public ModelUsage usage() { return usage; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UsageObserved)) return false;
        UsageObserved that = (UsageObserved) other;
        return java.util.Objects.equals(callId, that.callId) && java.util.Objects.equals(modelIdentifier, that.modelIdentifier) && java.util.Objects.equals(usage, that.usage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(callId);
        hash = 31 * hash + java.util.Objects.hashCode(modelIdentifier);
        hash = 31 * hash + java.util.Objects.hashCode(usage);
        return hash;
    }
    @Override public String toString() { return "UsageObserved[callId=" + callId + ", modelIdentifier=" + modelIdentifier + ", usage=" + usage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UsageObserved> schema() {
            return new dev.openallay.value.ValueSchema<>(UsageObserved.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UsageObserved>>asList(new dev.openallay.value.ValueSchema.Component<>(UsageObserved.class, "callId", UsageObserved::callId), new dev.openallay.value.ValueSchema.Component<>(UsageObserved.class, "modelIdentifier", UsageObserved::modelIdentifier), new dev.openallay.value.ValueSchema.Component<>(UsageObserved.class, "usage", UsageObserved::usage)), arguments -> new UsageObserved((java.util.UUID) arguments[0], (String) arguments[1], (ModelUsage) arguments[2]));
        }
    }
}

    /** Redacted lifecycle boundary. The relative attempt budget may be unavailable. */
    @dev.openallay.value.ValueType(AttemptStarted.ValueSchemaProvider.class)
public static final class AttemptStarted implements ModelEvent {
    private final int attempt;
    private final Long attemptTimeoutMillis;
    public AttemptStarted(int attempt, Long attemptTimeoutMillis) {

            if (attempt <= 0 || (attemptTimeoutMillis != null && attemptTimeoutMillis < 0)) {
                throw new IllegalArgumentException("Model attempt lifecycle is invalid");
            }

        this.attempt = attempt;
        this.attemptTimeoutMillis = attemptTimeoutMillis;
    }
    public int attempt() { return attempt; }
    public Long attemptTimeoutMillis() { return attemptTimeoutMillis; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AttemptStarted)) return false;
        AttemptStarted that = (AttemptStarted) other;
        return attempt == that.attempt && java.util.Objects.equals(attemptTimeoutMillis, that.attemptTimeoutMillis);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(attempt);
        hash = 31 * hash + java.util.Objects.hashCode(attemptTimeoutMillis);
        return hash;
    }
    @Override public String toString() { return "AttemptStarted[attempt=" + attempt + ", attemptTimeoutMillis=" + attemptTimeoutMillis + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<AttemptStarted> schema() {
            return new dev.openallay.value.ValueSchema<>(AttemptStarted.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<AttemptStarted>>asList(new dev.openallay.value.ValueSchema.Component<>(AttemptStarted.class, "attempt", AttemptStarted::attempt), new dev.openallay.value.ValueSchema.Component<>(AttemptStarted.class, "attemptTimeoutMillis", AttemptStarted::attemptTimeoutMillis)), arguments -> new AttemptStarted((Integer) arguments[0], (Long) arguments[1]));
        }
    }
}

    /** Successful response headers arrived; model response-body decoding is about to begin. */
    @dev.openallay.value.ValueType(ResponseStarted.ValueSchemaProvider.class)
public static final class ResponseStarted implements ModelEvent {
    public ResponseStarted() {
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ResponseStarted)) return false;
        ResponseStarted that = (ResponseStarted) other;
        return true;
    }
    @Override public int hashCode() {
        int hash = 0;
        return hash;
    }
    @Override public String toString() { return "ResponseStarted[]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ResponseStarted> schema() {
            return new dev.openallay.value.ValueSchema<>(ResponseStarted.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ResponseStarted>>asList(), arguments -> new ResponseStarted());
        }
    }
}

    @dev.openallay.value.ValueType(RateLimited.ValueSchemaProvider.class)
public static final class RateLimited implements ModelEvent {
    private final long retryAfterMillis;
    private final int attempt;
    public RateLimited(long retryAfterMillis, int attempt) {

            if (retryAfterMillis < 0 || attempt <= 0) {
                throw new IllegalArgumentException("Rate-limit delay and attempt are invalid");
            }

        this.retryAfterMillis = retryAfterMillis;
        this.attempt = attempt;
    }
    public long retryAfterMillis() { return retryAfterMillis; }
    public int attempt() { return attempt; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RateLimited)) return false;
        RateLimited that = (RateLimited) other;
        return retryAfterMillis == that.retryAfterMillis && attempt == that.attempt;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(retryAfterMillis);
        hash = 31 * hash + Integer.hashCode(attempt);
        return hash;
    }
    @Override public String toString() { return "RateLimited[retryAfterMillis=" + retryAfterMillis + ", attempt=" + attempt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RateLimited> schema() {
            return new dev.openallay.value.ValueSchema<>(RateLimited.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RateLimited>>asList(new dev.openallay.value.ValueSchema.Component<>(RateLimited.class, "retryAfterMillis", RateLimited::retryAfterMillis), new dev.openallay.value.ValueSchema.Component<>(RateLimited.class, "attempt", RateLimited::attempt)), arguments -> new RateLimited((Long) arguments[0], (Integer) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(MessageComplete.ValueSchemaProvider.class)
public static final class MessageComplete implements ModelEvent {
    private final String stopReason;
    public MessageComplete(String stopReason) {
        this.stopReason = stopReason;
    }
    public String stopReason() { return stopReason; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MessageComplete)) return false;
        MessageComplete that = (MessageComplete) other;
        return java.util.Objects.equals(stopReason, that.stopReason);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(stopReason);
        return hash;
    }
    @Override public String toString() { return "MessageComplete[stopReason=" + stopReason + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<MessageComplete> schema() {
            return new dev.openallay.value.ValueSchema<>(MessageComplete.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<MessageComplete>>asList(new dev.openallay.value.ValueSchema.Component<>(MessageComplete.class, "stopReason", MessageComplete::stopReason)), arguments -> new MessageComplete((String) arguments[0]));
        }
    }
}
}
