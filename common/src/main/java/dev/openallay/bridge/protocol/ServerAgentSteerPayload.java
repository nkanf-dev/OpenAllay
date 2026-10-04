package dev.openallay.bridge.protocol;

import dev.openallay.model.ModelContent;
import java.util.Objects;
import java.util.UUID;

/** An inbox edit for one server request. PUT also replaces an unconsumed entry. */
public record ServerAgentSteerPayload(
        UUID requestId,
        UUID messageId,
        Operation operation,
        ServerAgentHistoryMessage message,
        java.util.List<ServerAgentImageAttachment> imageAttachments) {
    public enum Operation { PUT, REMOVE }

    public ServerAgentSteerPayload {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(operation, "operation");
        imageAttachments = java.util.List.copyOf(imageAttachments);
        if (operation == Operation.PUT) {
            Objects.requireNonNull(message, "message");
            validateMessage(message);
            java.util.Map<String, dev.openallay.model.image.ImageReference> required = new java.util.LinkedHashMap<>();
            for (var content : message.toModelMessage().content()) {
                if (content instanceof ModelContent.Image image) {
                    var previous = required.putIfAbsent(image.reference().sha256(), image.reference());
                    if (previous != null && !previous.equals(image.reference())) {
                        throw new IllegalArgumentException("Conflicting steer image references");
                    }
                }
            }
            java.util.Map<String, dev.openallay.model.image.ImageReference> uploaded = new java.util.LinkedHashMap<>();
            for (var attachment : imageAttachments) {
                if (uploaded.putIfAbsent(attachment.reference().sha256(), attachment.reference()) != null) {
                    throw new IllegalArgumentException("Duplicate steer image attachment");
                }
            }
            if (!required.equals(uploaded)) {
                throw new IllegalArgumentException("Steer image attachments must exactly match its references");
            }
        } else if (message != null || !imageAttachments.isEmpty()) {
            throw new IllegalArgumentException("REMOVE must not contain a message or images");
        }
    }

    public ServerAgentSteerPayload(UUID requestId, UUID messageId, Operation operation,
            ServerAgentHistoryMessage message) {
        this(requestId, messageId, operation, message, java.util.List.of());
    }

    public static void validateMessage(ServerAgentHistoryMessage message) {
        dev.openallay.model.ModelMessage.requireUserInput(message.toModelMessage());
    }
}
