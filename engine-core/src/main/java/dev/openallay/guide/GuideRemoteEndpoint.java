package dev.openallay.guide;

import dev.openallay.agent.AgentEvent;
import dev.openallay.model.ModelMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface GuideRemoteEndpoint {
    boolean serverModelAvailable();

    boolean serverToolsAvailable();

    default dev.openallay.model.image.ImageInputCapability imageInputCapability() {
        return dev.openallay.model.image.ImageInputCapability.UNKNOWN;
    }

    default String imageInputCapabilitySource() {
        return "unknown";
    }

    default Optional<GuideContextSpec> contextSpec() {
        return Optional.empty();
    }

    boolean ask(
            UUID requestId, String sessionId, String question, Consumer<AgentEvent> events);

    default boolean askWithContext(
            UUID requestId,
            String sessionId,
            String question,
            List<ModelMessage> history,
            Consumer<AgentEvent> events) {
        return ask(requestId, sessionId, question, events);
    }

    default boolean ask(
            UUID requestId, String sessionId, ModelMessage userInput,
            dev.openallay.model.image.ImagePayloadResolver images, Consumer<AgentEvent> events) {
        return askWithContext(requestId, sessionId, userInput, images, dev.openallay.util.Java8Collections.listOf(), events);
    }

    default boolean askWithContext(
            UUID requestId, String sessionId, ModelMessage userInput,
            dev.openallay.model.image.ImagePayloadResolver images, List<ModelMessage> history,
            Consumer<AgentEvent> events) {
        dev.openallay.agent.AgentRequest.validateUserInput(userInput);
        boolean containsImages = dev.openallay.model.image.ModelImages.hasImages(
                dev.openallay.util.Java8Collections.toList(java.util.stream.Stream.concat(history.stream(), java.util.stream.Stream.of(userInput))));
        if (containsImages) {
            throw new GuideModelProfileException(
                    "image_input_unsupported", "This remote endpoint does not support typed image input");
        }
        return askWithContext(requestId, sessionId,
                dev.openallay.agent.AgentRequest.displayText(userInput), history, events);
    }

    default boolean steer(UUID requestId, UUID messageId, ModelMessage message) { return false; }

    default boolean steer(UUID requestId, UUID messageId, ModelMessage message,
            dev.openallay.model.image.ImagePayloadResolver images) {
        return steer(requestId, messageId, message);
    }

    default boolean editSteer(UUID requestId, UUID messageId, ModelMessage message,
            dev.openallay.model.image.ImagePayloadResolver images) {
        return steer(requestId, messageId, message, images);
    }

    default boolean editSteer(UUID requestId, UUID messageId, ModelMessage message) {
        return steer(requestId, messageId, message);
    }

    default boolean cancelSteer(UUID requestId, UUID messageId) { return false; }

    boolean cancel(UUID requestId);

    void disconnect();
}
