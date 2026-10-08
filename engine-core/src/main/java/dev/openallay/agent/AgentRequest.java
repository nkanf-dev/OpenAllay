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
@dev.openallay.value.ValueType(AgentRequest.ValueSchemaProvider.class)
public final class AgentRequest {
    private final UUID requestId;
    private final UUID actorId;
    private final String sessionId;
    private final ModelMessage userInput;
    private final String systemPrompt;
    private final ToolInvocationContext context;
    private final boolean stream;
    private final ImagePayloadResolver images;
    public AgentRequest(UUID requestId, UUID actorId, String sessionId, ModelMessage userInput, String systemPrompt, ToolInvocationContext context, boolean stream, ImagePayloadResolver images) {

        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(actorId, "actorId");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid Agent session ID: " + sessionId);
        }
        validateUserInput(userInput);
        if (userInput.inputObservation().isPresent()
                && !actorId.equals(userInput.inputObservation().get().focus().actorId())) {
            throw new IllegalArgumentException("Input reference belongs to another player");
        }
        if (systemPrompt == null || dev.openallay.util.Java8Strings.isBlank(systemPrompt)) {
            throw new IllegalArgumentException("Agent system prompt must not be blank");
        }
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(images, "images");

        this.requestId = requestId;
        this.actorId = actorId;
        this.sessionId = sessionId;
        this.userInput = userInput;
        this.systemPrompt = systemPrompt;
        this.context = context;
        this.stream = stream;
        this.images = images;
    }
    public UUID requestId() { return requestId; }
    public UUID actorId() { return actorId; }
    public String sessionId() { return sessionId; }
    public ModelMessage userInput() { return userInput; }
    public String systemPrompt() { return systemPrompt; }
    public ToolInvocationContext context() { return context; }
    public boolean stream() { return stream; }
    public ImagePayloadResolver images() { return images; }
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
public String userMessage() {
        return displayText(userInput);
    }
public static String displayText(ModelMessage input) {
        String text = input.content().stream()
                .filter(ModelContent.Text.class::isInstance)
                .map(ModelContent.Text.class::cast)
                .map(ModelContent.Text::text)
                .collect(java.util.stream.Collectors.joining("\n"));
        return dev.openallay.util.Java8Strings.isBlank(text) ? "[Image]" : text;
    }
public static void validateUserInput(ModelMessage input) {
        Objects.requireNonNull(input, "userInput");
        ModelMessage.requireUserInput(input);
        if (input.role() != ModelRole.USER
                || input.content().stream().anyMatch(content ->
                        !(content instanceof ModelContent.Text)
                                && !(content instanceof ModelContent.Image))) {
            throw new IllegalArgumentException("Agent user input must contain only user text or images");
        }
        if (!dev.openallay.model.image.ModelImages.hasImages(dev.openallay.util.Java8Collections.listOf(input))
                && input.content().stream().noneMatch(content ->
                        content instanceof ModelContent.Text && !dev.openallay.util.Java8Strings.isBlank(((ModelContent.Text) content).text()))) {
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AgentRequest)) return false;
        AgentRequest that = (AgentRequest) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(userInput, that.userInput) && java.util.Objects.equals(systemPrompt, that.systemPrompt) && java.util.Objects.equals(context, that.context) && stream == that.stream && java.util.Objects.equals(images, that.images);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(userInput);
        hash = 31 * hash + java.util.Objects.hashCode(systemPrompt);
        hash = 31 * hash + java.util.Objects.hashCode(context);
        hash = 31 * hash + Boolean.hashCode(stream);
        hash = 31 * hash + java.util.Objects.hashCode(images);
        return hash;
    }
    @Override public String toString() { return "AgentRequest[requestId=" + requestId + ", actorId=" + actorId + ", sessionId=" + sessionId + ", userInput=" + userInput + ", systemPrompt=" + systemPrompt + ", context=" + context + ", stream=" + stream + ", images=" + images + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<AgentRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(AgentRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<AgentRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(AgentRequest.class, "requestId", AgentRequest::requestId), new dev.openallay.value.ValueSchema.Component<>(AgentRequest.class, "actorId", AgentRequest::actorId), new dev.openallay.value.ValueSchema.Component<>(AgentRequest.class, "sessionId", AgentRequest::sessionId), new dev.openallay.value.ValueSchema.Component<>(AgentRequest.class, "userInput", AgentRequest::userInput), new dev.openallay.value.ValueSchema.Component<>(AgentRequest.class, "systemPrompt", AgentRequest::systemPrompt), new dev.openallay.value.ValueSchema.Component<>(AgentRequest.class, "context", AgentRequest::context), new dev.openallay.value.ValueSchema.Component<>(AgentRequest.class, "stream", AgentRequest::stream), new dev.openallay.value.ValueSchema.Component<>(AgentRequest.class, "images", AgentRequest::images)), arguments -> new AgentRequest((UUID) arguments[0], (UUID) arguments[1], (String) arguments[2], (ModelMessage) arguments[3], (String) arguments[4], (ToolInvocationContext) arguments[5], (Boolean) arguments[6], (ImagePayloadResolver) arguments[7]));
        }
    }
}
