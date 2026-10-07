package dev.openallay.guide;

import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.time.Instant;
import java.util.UUID;

/** Session-local player draft. It is not a provider message or a durable history row. */
@dev.openallay.value.ValueType(GuidePendingMessage.ValueSchemaProvider.class)
public final class GuidePendingMessage {
    private final UUID id;
    private final Kind kind;
    private final ModelMessage message;
    private final Instant createdAt;
    private final UUID requestId;
    private final GuideFailure failure;
    public GuidePendingMessage(UUID id, Kind kind, ModelMessage message, Instant createdAt, UUID requestId, GuideFailure failure) {

        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(kind, "kind");
        java.util.Objects.requireNonNull(message, "message");
        java.util.Objects.requireNonNull(createdAt, "createdAt");
        if (message.role() != ModelRole.USER) throw new IllegalArgumentException("pending input must be USER");
        if (kind == Kind.STEER && requestId == null) throw new IllegalArgumentException("steer requires request ID");

        this.id = id;
        this.kind = kind;
        this.message = message;
        this.createdAt = createdAt;
        this.requestId = requestId;
        this.failure = failure;
    }
    public UUID id() { return id; }
    public Kind kind() { return kind; }
    public ModelMessage message() { return message; }
    public Instant createdAt() { return createdAt; }
    public UUID requestId() { return requestId; }
    public GuideFailure failure() { return failure; }
public GuidePendingMessage(UUID id, Kind kind, ModelMessage message, Instant createdAt, UUID requestId) {
        this(id, kind, message, createdAt, requestId, null);
    }
public enum Kind { FOLLOW_UP, STEER }
public String text() { return displayText(message); }
public GuidePendingMessage followUp() {
        return new GuidePendingMessage(id, Kind.FOLLOW_UP, message, createdAt, null, failure);
    }
public GuidePendingMessage withMessage(ModelMessage replacement) {
        return new GuidePendingMessage(id, kind, replacement, createdAt, requestId);
    }
public GuidePendingMessage failed(GuideFailure failure) {
        return new GuidePendingMessage(id, kind, message, createdAt, requestId, failure);
    }
public static String displayText(ModelMessage message) {
        String text = message.content().stream().filter(ModelContent.Text.class::isInstance)
                .map(ModelContent.Text.class::cast).map(ModelContent.Text::text)
                .collect(java.util.stream.Collectors.joining("\n"));
        return dev.openallay.util.Java8Strings.isBlank(text) ? "[Image attachment]" : text;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuidePendingMessage)) return false;
        GuidePendingMessage that = (GuidePendingMessage) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(message, that.message) && java.util.Objects.equals(createdAt, that.createdAt) && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        hash = 31 * hash + java.util.Objects.hashCode(createdAt);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "GuidePendingMessage[id=" + id + ", kind=" + kind + ", message=" + message + ", createdAt=" + createdAt + ", requestId=" + requestId + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuidePendingMessage> schema() {
            return new dev.openallay.value.ValueSchema<>(GuidePendingMessage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuidePendingMessage>>asList(new dev.openallay.value.ValueSchema.Component<>(GuidePendingMessage.class, "id", GuidePendingMessage::id), new dev.openallay.value.ValueSchema.Component<>(GuidePendingMessage.class, "kind", GuidePendingMessage::kind), new dev.openallay.value.ValueSchema.Component<>(GuidePendingMessage.class, "message", GuidePendingMessage::message), new dev.openallay.value.ValueSchema.Component<>(GuidePendingMessage.class, "createdAt", GuidePendingMessage::createdAt), new dev.openallay.value.ValueSchema.Component<>(GuidePendingMessage.class, "requestId", GuidePendingMessage::requestId), new dev.openallay.value.ValueSchema.Component<>(GuidePendingMessage.class, "failure", GuidePendingMessage::failure)), arguments -> new GuidePendingMessage((UUID) arguments[0], (Kind) arguments[1], (ModelMessage) arguments[2], (Instant) arguments[3], (UUID) arguments[4], (GuideFailure) arguments[5]));
        }
    }
}
