package dev.openallay.bridge.protocol;

import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.util.List;

public record ServerAgentHistoryMessage(Role role, List<ServerAgentHistoryContent> content,
        @com.google.gson.annotations.JsonAdapter(value = dev.openallay.world.ClientObservationAnchorJson.OptionalAdapter.class, nullSafe = false)
        java.util.Optional<dev.openallay.world.ClientObservationAnchor> inputObservation) {
    public ServerAgentHistoryMessage(Role role, List<ServerAgentHistoryContent> content) {
        this(role, content, java.util.Optional.empty());
    }
    public enum Role {
        USER,
        ASSISTANT
    }

    public ServerAgentHistoryMessage {
        java.util.Objects.requireNonNull(role, "role");
        content = List.copyOf(content);
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
                    content.stream().map(ServerAgentHistoryContent::toModelContent).toList(), inputObservation));
        }
    }

    public ServerAgentHistoryMessage(Role role, String text) {
        this(role, List.of(new ServerAgentHistoryContent(
                ServerAgentHistoryContent.Kind.TEXT, text, null, null, null, null)));
    }

    public static ServerAgentHistoryMessage from(ModelMessage message) {
        if (message.content().stream().anyMatch(
                dev.openallay.model.ModelContent.Reasoning.class::isInstance)) {
            throw new IllegalArgumentException("Reasoning cannot enter bridge history");
        }
        return new ServerAgentHistoryMessage(
                message.role() == ModelRole.USER ? Role.USER : Role.ASSISTANT,
                message.content().stream().map(ServerAgentHistoryContent::from).toList(), message.inputObservation());
    }

    public ModelMessage toModelMessage() {
        return new ModelMessage(
                role == Role.USER ? ModelRole.USER : ModelRole.ASSISTANT,
                content.stream().map(ServerAgentHistoryContent::toModelContent).toList(), inputObservation);
    }
}
