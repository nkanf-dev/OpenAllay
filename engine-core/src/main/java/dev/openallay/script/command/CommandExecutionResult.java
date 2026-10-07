package dev.openallay.script.command;

import java.util.List;
import java.util.UUID;

/**
 * Result of one player command after its client-visible feedback window closes.
 *
 * <p>Minecraft does not put a correlation ID or a universal success bit on command feedback.
 * The result therefore reports the observed messages without claiming more authority than the
 * client actually received.
 */
@dev.openallay.value.ValueType(CommandExecutionResult.ValueSchemaProvider.class)
public final class CommandExecutionResult {
    private final long sequence;
    private final UUID actorId;
    private final String command;
    private final String state;
    private final List<String> messages;
    private final boolean feedbackObserved;
    private final long durationMillis;
    public CommandExecutionResult(long sequence, UUID actorId, String command, String state, List<String> messages, boolean feedbackObserved, long durationMillis) {

        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null");
        }
        if (command == null || dev.openallay.util.Java8Strings.isBlank(command)) {
            throw new IllegalArgumentException("command must not be blank");
        }
        if (!"feedback".equals(state) && !"no_feedback".equals(state)) {
            throw new IllegalArgumentException("state must be feedback or no_feedback");
        }
        messages = dev.openallay.util.Java8Collections.listCopyOf(messages);
        if ("feedback".equals(state) != !messages.isEmpty()
                || feedbackObserved != !messages.isEmpty()) {
            throw new IllegalArgumentException("state and messages disagree");
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }

        this.sequence = sequence;
        this.actorId = actorId;
        this.command = command;
        this.state = state;
        this.messages = messages;
        this.feedbackObserved = feedbackObserved;
        this.durationMillis = durationMillis;
    }
    public long sequence() { return sequence; }
    public UUID actorId() { return actorId; }
    public String command() { return command; }
    public String state() { return state; }
    public List<String> messages() { return messages; }
    public boolean feedbackObserved() { return feedbackObserved; }
    public long durationMillis() { return durationMillis; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CommandExecutionResult)) return false;
        CommandExecutionResult that = (CommandExecutionResult) other;
        return sequence == that.sequence && java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(command, that.command) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(messages, that.messages) && feedbackObserved == that.feedbackObserved && durationMillis == that.durationMillis;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(sequence);
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(command);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + Boolean.hashCode(feedbackObserved);
        hash = 31 * hash + Long.hashCode(durationMillis);
        return hash;
    }
    @Override public String toString() { return "CommandExecutionResult[sequence=" + sequence + ", actorId=" + actorId + ", command=" + command + ", state=" + state + ", messages=" + messages + ", feedbackObserved=" + feedbackObserved + ", durationMillis=" + durationMillis + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CommandExecutionResult> schema() {
            return new dev.openallay.value.ValueSchema<>(CommandExecutionResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CommandExecutionResult>>asList(new dev.openallay.value.ValueSchema.Component<>(CommandExecutionResult.class, "sequence", CommandExecutionResult::sequence), new dev.openallay.value.ValueSchema.Component<>(CommandExecutionResult.class, "actorId", CommandExecutionResult::actorId), new dev.openallay.value.ValueSchema.Component<>(CommandExecutionResult.class, "command", CommandExecutionResult::command), new dev.openallay.value.ValueSchema.Component<>(CommandExecutionResult.class, "state", CommandExecutionResult::state), new dev.openallay.value.ValueSchema.Component<>(CommandExecutionResult.class, "messages", CommandExecutionResult::messages), new dev.openallay.value.ValueSchema.Component<>(CommandExecutionResult.class, "feedbackObserved", CommandExecutionResult::feedbackObserved), new dev.openallay.value.ValueSchema.Component<>(CommandExecutionResult.class, "durationMillis", CommandExecutionResult::durationMillis)), arguments -> new CommandExecutionResult((Long) arguments[0], (UUID) arguments[1], (String) arguments[2], (String) arguments[3], (List) arguments[4], (Boolean) arguments[5], (Long) arguments[6]));
        }
    }
}
