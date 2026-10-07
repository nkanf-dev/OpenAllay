package dev.openallay.bridge.protocol;

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
            dev.openallay.model.image.ModelImages.uniqueReferences(java.util.List.of(message.toModelMessage()))
                    .forEach(image -> required.put(image.sha256(), image));
            java.util.Map<String, dev.openallay.model.image.ImageReference> uploaded = new java.util.LinkedHashMap<>();
            for (dev.openallay.bridge.protocol.ServerAgentImageAttachment attachment : imageAttachments) {
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
