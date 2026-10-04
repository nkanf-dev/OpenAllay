package dev.openallay.guide;

import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.time.Instant;
import java.util.UUID;

/** Session-local player draft. It is not a provider message or a durable history row. */
public record GuidePendingMessage(
        UUID id, Kind kind, ModelMessage message, Instant createdAt, UUID requestId,
        GuideFailure failure) {
    public GuidePendingMessage(UUID id, Kind kind, ModelMessage message, Instant createdAt, UUID requestId) {
        this(id, kind, message, createdAt, requestId, null);
    }
    public enum Kind { FOLLOW_UP, STEER }

    public GuidePendingMessage {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(kind, "kind");
        java.util.Objects.requireNonNull(message, "message");
        java.util.Objects.requireNonNull(createdAt, "createdAt");
        if (message.role() != ModelRole.USER) throw new IllegalArgumentException("pending input must be USER");
        if (kind == Kind.STEER && requestId == null) throw new IllegalArgumentException("steer requires request ID");
    }

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
        return text.isBlank() ? "[Image attachment]" : text;
    }
}
