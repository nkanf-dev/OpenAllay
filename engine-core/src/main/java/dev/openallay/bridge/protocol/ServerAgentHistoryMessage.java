package dev.openallay.bridge.protocol;

import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.util.List;

@dev.openallay.value.ValueType(ServerAgentHistoryMessage.ValueSchemaProvider.class)
public final class ServerAgentHistoryMessage {
    private final Role role;
    private final List<ServerAgentHistoryContent> content;
    @com.google.gson.annotations.JsonAdapter(value = dev.openallay.world.ClientObservationAnchorJson.OptionalAdapter.class, nullSafe = false) private final java.util.Optional<dev.openallay.world.ClientObservationAnchor> inputObservation;
    public ServerAgentHistoryMessage(Role role, List<ServerAgentHistoryContent> content, java.util.Optional<dev.openallay.world.ClientObservationAnchor> inputObservation) {

        java.util.Objects.requireNonNull(role, "role");
        content = dev.openallay.util.Java8Collections.listCopyOf(content);
        java.util.Objects.requireNonNull(inputObservation, "inputObservation");
        if (content.isEmpty()) throw new IllegalArgumentException(
                "Server Agent history content must not be empty");
        if (role != Role.USER && content.stream().anyMatch(block ->
                block.kind() == ServerAgentHistoryContent.Kind.IMAGE)) {
            throw new IllegalArgumentException("Image history content must belong to a user message");
        }
        if (inputObservation.isPresent()) {
            dev.openallay.model.ModelMessage.requireUserInput(new ModelMessage(
                    role == Role.USER ? ModelRole.USER : ModelRole.ASSISTANT,
                    dev.openallay.util.Java8Collections.toList(content.stream().map(ServerAgentHistoryContent::toModelContent)), inputObservation));
        }

        this.role = role;
        this.content = content;
        this.inputObservation = inputObservation;
    }
    public Role role() { return role; }
    public List<ServerAgentHistoryContent> content() { return content; }
    public java.util.Optional<dev.openallay.world.ClientObservationAnchor> inputObservation() { return inputObservation; }
public ServerAgentHistoryMessage(Role role, List<ServerAgentHistoryContent> content) {
        this(role, content, java.util.Optional.empty());
    }
public enum Role {
        USER,
        ASSISTANT
    }
public ServerAgentHistoryMessage(Role role, String text) {
        this(role, dev.openallay.util.Java8Collections.listOf(new ServerAgentHistoryContent(
                ServerAgentHistoryContent.Kind.TEXT, text, null, null, null, null)));
    }
public static ServerAgentHistoryMessage from(ModelMessage message) {
        if (message.content().stream().anyMatch(
                dev.openallay.model.ModelContent.Reasoning.class::isInstance)) {
            throw new IllegalArgumentException("Reasoning cannot enter bridge history");
        }
        return new ServerAgentHistoryMessage(
                message.role() == ModelRole.USER ? Role.USER : Role.ASSISTANT,
                dev.openallay.util.Java8Collections.toList(message.content().stream().map(ServerAgentHistoryContent::from)), message.inputObservation());
    }
public ModelMessage toModelMessage() {
        return new ModelMessage(
                role == Role.USER ? ModelRole.USER : ModelRole.ASSISTANT,
                dev.openallay.util.Java8Collections.toList(content.stream().map(ServerAgentHistoryContent::toModelContent)), inputObservation);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerAgentHistoryMessage)) return false;
        ServerAgentHistoryMessage that = (ServerAgentHistoryMessage) other;
        return java.util.Objects.equals(role, that.role) && java.util.Objects.equals(content, that.content) && java.util.Objects.equals(inputObservation, that.inputObservation);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(role);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        hash = 31 * hash + java.util.Objects.hashCode(inputObservation);
        return hash;
    }
    @Override public String toString() { return "ServerAgentHistoryMessage[role=" + role + ", content=" + content + ", inputObservation=" + inputObservation + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerAgentHistoryMessage> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerAgentHistoryMessage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerAgentHistoryMessage>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryMessage.class, "role", ServerAgentHistoryMessage::role), new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryMessage.class, "content", ServerAgentHistoryMessage::content), new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryMessage.class, "inputObservation", ServerAgentHistoryMessage::inputObservation)), arguments -> new ServerAgentHistoryMessage((Role) arguments[0], (List) arguments[1], (java.util.Optional) arguments[2]));
        }
    }
}
