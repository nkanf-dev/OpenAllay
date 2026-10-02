package dev.openallay.agent;

import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.image.ImagePayloadResolver;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Typed user input and a request-only, actor-scoped image resolver. */
public record AgentRequest(
        UUID requestId,
        UUID actorId,
        String sessionId,
        ModelMessage userInput,
        String systemPrompt,
        ToolInvocationContext context,
        boolean stream,
        ImagePayloadResolver images) {
    public AgentRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(actorId, "actorId");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid Agent session ID: " + sessionId);
        }
        validateUserInput(userInput);
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("Agent system prompt must not be blank");
        }
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(images, "images");
    }

    public AgentRequest(
            UUID requestId, UUID actorId, String sessionId, String userMessage,
            String systemPrompt, ToolInvocationContext context, boolean stream) {
        this(requestId, actorId, sessionId, ModelMessage.userText(userMessage),
                systemPrompt, context, stream, unavailableImages());
    }

    public AgentRequest(
            UUID requestId, UUID actorId, String sessionId, ModelMessage userInput,
            ImagePayloadResolver images, String systemPrompt,
            ToolInvocationContext context, boolean stream) {
        this(requestId, actorId, sessionId, userInput, systemPrompt, context, stream, images);
    }

    /** Text-only compatibility view. This label never replaces the typed model input. */
    public String userMessage() {
        return displayText(userInput);
    }

    public static String displayText(ModelMessage input) {
        String text = input.content().stream()
                .filter(ModelContent.Text.class::isInstance)
                .map(ModelContent.Text.class::cast)
                .map(ModelContent.Text::text)
                .collect(java.util.stream.Collectors.joining("\n"));
        return text.isBlank() ? "[Image]" : text;
    }

    public static void validateUserInput(ModelMessage input) {
        Objects.requireNonNull(input, "userInput");
        if (input.role() != ModelRole.USER
                || input.content().stream().anyMatch(content ->
                        !(content instanceof ModelContent.Text)
                                && !(content instanceof ModelContent.Image))) {
            throw new IllegalArgumentException("Agent user input must contain only user text or images");
        }
        if (input.content().stream().noneMatch(content ->
                content instanceof ModelContent.Image
                        || content instanceof ModelContent.Text text && !text.text().isBlank())) {
            throw new IllegalArgumentException("Agent user input must not be blank");
        }
    }

    public static ImagePayloadResolver unavailableImages() {
        return reference -> {
            throw new IOException("No image payload resolver is available for this request");
        };
    }

    public dev.openallay.agent.session.AgentSessionKey sessionKey() {
        return new dev.openallay.agent.session.AgentSessionKey(actorId, sessionId);
    }
}
