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
public record CommandExecutionResult(
        long sequence,
        UUID actorId,
        String command,
        String state,
        List<String> messages,
        boolean feedbackObserved,
        long durationMillis) {
    public CommandExecutionResult {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }
        if (!"feedback".equals(state) && !"no_feedback".equals(state)) {
            throw new IllegalArgumentException("state must be feedback or no_feedback");
        }
        messages = List.copyOf(messages);
        if ("feedback".equals(state) != !messages.isEmpty()
                || feedbackObserved != !messages.isEmpty()) {
            throw new IllegalArgumentException("state and messages disagree");
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
    }

}
