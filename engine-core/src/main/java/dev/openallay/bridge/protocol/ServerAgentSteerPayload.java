package dev.openallay.bridge.protocol;

import java.util.Objects;
import java.util.UUID;

/** An inbox edit for one server request. PUT also replaces an unconsumed entry. */
@dev.openallay.value.ValueType(ServerAgentSteerPayload.ValueSchemaProvider.class)
public final class ServerAgentSteerPayload {
    private final UUID requestId;
    private final UUID messageId;
    private final Operation operation;
    private final ServerAgentHistoryMessage message;
    private final java.util.List<ServerAgentImageAttachment> imageAttachments;
    public ServerAgentSteerPayload(UUID requestId, UUID messageId, Operation operation, ServerAgentHistoryMessage message, java.util.List<ServerAgentImageAttachment> imageAttachments) {

        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(operation, "operation");
        imageAttachments = dev.openallay.util.Java8Collections.listCopyOf(imageAttachments);
        if (operation == Operation.PUT) {
            Objects.requireNonNull(message, "message");
            validateMessage(message);
            java.util.Map<String, dev.openallay.model.image.ImageReference> required = new java.util.LinkedHashMap<>();
            dev.openallay.model.image.ModelImages.uniqueReferences(dev.openallay.util.Java8Collections.listOf(message.toModelMessage()))
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

        this.requestId = requestId;
        this.messageId = messageId;
        this.operation = operation;
        this.message = message;
        this.imageAttachments = imageAttachments;
    }
    public UUID requestId() { return requestId; }
    public UUID messageId() { return messageId; }
    public Operation operation() { return operation; }
    public ServerAgentHistoryMessage message() { return message; }
    public java.util.List<ServerAgentImageAttachment> imageAttachments() { return imageAttachments; }
public enum Operation { PUT, REMOVE }
public ServerAgentSteerPayload(UUID requestId, UUID messageId, Operation operation,
            ServerAgentHistoryMessage message) {
        this(requestId, messageId, operation, message, dev.openallay.util.Java8Collections.listOf());
    }
public static void validateMessage(ServerAgentHistoryMessage message) {
        dev.openallay.model.ModelMessage.requireUserInput(message.toModelMessage());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerAgentSteerPayload)) return false;
        ServerAgentSteerPayload that = (ServerAgentSteerPayload) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(messageId, that.messageId) && java.util.Objects.equals(operation, that.operation) && java.util.Objects.equals(message, that.message) && java.util.Objects.equals(imageAttachments, that.imageAttachments);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        hash = 31 * hash + java.util.Objects.hashCode(operation);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        hash = 31 * hash + java.util.Objects.hashCode(imageAttachments);
        return hash;
    }
    @Override public String toString() { return "ServerAgentSteerPayload[requestId=" + requestId + ", messageId=" + messageId + ", operation=" + operation + ", message=" + message + ", imageAttachments=" + imageAttachments + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerAgentSteerPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerAgentSteerPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerAgentSteerPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerPayload.class, "requestId", ServerAgentSteerPayload::requestId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerPayload.class, "messageId", ServerAgentSteerPayload::messageId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerPayload.class, "operation", ServerAgentSteerPayload::operation), new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerPayload.class, "message", ServerAgentSteerPayload::message), new dev.openallay.value.ValueSchema.Component<>(ServerAgentSteerPayload.class, "imageAttachments", ServerAgentSteerPayload::imageAttachments)), arguments -> new ServerAgentSteerPayload((UUID) arguments[0], (UUID) arguments[1], (Operation) arguments[2], (ServerAgentHistoryMessage) arguments[3], (java.util.List) arguments[4]));
        }
    }
}
