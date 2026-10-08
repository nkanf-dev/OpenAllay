package dev.openallay.guide;

import java.time.Instant;
import java.util.UUID;

@dev.openallay.value.ValueType(GuideMessage.ValueSchemaProvider.class)
public final class GuideMessage {
    private final UUID requestId;
    private final Role role;
    private final String text;
    private final Instant createdAt;
    public GuideMessage(UUID requestId, Role role, String text, Instant createdAt) {

        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(role, "role");
        if (text == null || dev.openallay.util.Java8Strings.isBlank(text)) {
            throw new IllegalArgumentException("message text must not be blank");
        }
        java.util.Objects.requireNonNull(createdAt, "createdAt");

        this.requestId = requestId;
        this.role = role;
        this.text = text;
        this.createdAt = createdAt;
    }
    public UUID requestId() { return requestId; }
    public Role role() { return role; }
    public String text() { return text; }
    public Instant createdAt() { return createdAt; }
public enum Role { USER, ASSISTANT }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideMessage)) return false;
        GuideMessage that = (GuideMessage) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(role, that.role) && java.util.Objects.equals(text, that.text) && java.util.Objects.equals(createdAt, that.createdAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(role);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(createdAt);
        return hash;
    }
    @Override public String toString() { return "GuideMessage[requestId=" + requestId + ", role=" + role + ", text=" + text + ", createdAt=" + createdAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideMessage> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideMessage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideMessage>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideMessage.class, "requestId", GuideMessage::requestId), new dev.openallay.value.ValueSchema.Component<>(GuideMessage.class, "role", GuideMessage::role), new dev.openallay.value.ValueSchema.Component<>(GuideMessage.class, "text", GuideMessage::text), new dev.openallay.value.ValueSchema.Component<>(GuideMessage.class, "createdAt", GuideMessage::createdAt)), arguments -> new GuideMessage((UUID) arguments[0], (Role) arguments[1], (String) arguments[2], (Instant) arguments[3]));
        }
    }
}
