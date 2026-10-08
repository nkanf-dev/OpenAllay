package dev.openallay.model;

import dev.openallay.model.image.ImagePayloadResolver;
import java.util.List;
import java.util.Objects;

/** Provider input plus request-only scoped asset access; resolvers are not transcript data. */
@dev.openallay.value.ValueType(ModelRequest.ValueSchemaProvider.class)
public final class ModelRequest {
    private final String systemPrompt;
    private final List<ModelMessage> messages;
    private final List<ModelToolDefinition> tools;
    private final boolean stream;
    private final String sessionKey;
    private final Integer maxOutputTokens;
    private final ImagePayloadResolver images;
    public ModelRequest(String systemPrompt, List<ModelMessage> messages, List<ModelToolDefinition> tools, boolean stream, String sessionKey, Integer maxOutputTokens, ImagePayloadResolver images) {

        if (systemPrompt == null || dev.openallay.util.Java8Strings.isBlank(systemPrompt)) {
            throw new IllegalArgumentException("System prompt must not be blank");
        }
        messages = dev.openallay.util.Java8Collections.listCopyOf(messages);
        tools = dev.openallay.util.Java8Collections.listCopyOf(tools);
        Objects.requireNonNull(images, "images");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("At least one model message is required");
        }
        if (sessionKey == null || dev.openallay.util.Java8Strings.isBlank(sessionKey)) {
            throw new IllegalArgumentException("Model request sessionKey must not be blank");
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("Model request maxOutputTokens must be positive");
        }

        this.systemPrompt = systemPrompt;
        this.messages = messages;
        this.tools = tools;
        this.stream = stream;
        this.sessionKey = sessionKey;
        this.maxOutputTokens = maxOutputTokens;
        this.images = images;
    }
    public String systemPrompt() { return systemPrompt; }
    public List<ModelMessage> messages() { return messages; }
    public List<ModelToolDefinition> tools() { return tools; }
    public boolean stream() { return stream; }
    public String sessionKey() { return sessionKey; }
    public Integer maxOutputTokens() { return maxOutputTokens; }
    public ImagePayloadResolver images() { return images; }
public ModelRequest(
            String systemPrompt,
            List<ModelMessage> messages,
            List<ModelToolDefinition> tools,
            boolean stream,
            String sessionKey,
            Integer maxOutputTokens) {
        this(systemPrompt, messages, tools, stream, sessionKey, maxOutputTokens,
                ImagePayloadResolver.unavailable());
    }
public ModelRequest(
            String systemPrompt,
            List<ModelMessage> messages,
            List<ModelToolDefinition> tools,
            boolean stream,
            String sessionKey) {
        this(systemPrompt, messages, tools, stream, sessionKey, null,
                ImagePayloadResolver.unavailable());
    }
public int effectiveMaxOutputTokens(int configured) {
        return maxOutputTokens == null ? configured : Math.min(configured, maxOutputTokens);
    }
public ModelRequest(
            String systemPrompt,
            List<ModelMessage> messages,
            List<ModelToolDefinition> tools,
            boolean stream) {
        this(systemPrompt, messages, tools, stream, "default");
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelRequest)) return false;
        ModelRequest that = (ModelRequest) other;
        return java.util.Objects.equals(systemPrompt, that.systemPrompt) && java.util.Objects.equals(messages, that.messages) && java.util.Objects.equals(tools, that.tools) && stream == that.stream && java.util.Objects.equals(sessionKey, that.sessionKey) && java.util.Objects.equals(maxOutputTokens, that.maxOutputTokens) && java.util.Objects.equals(images, that.images);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(systemPrompt);
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + java.util.Objects.hashCode(tools);
        hash = 31 * hash + Boolean.hashCode(stream);
        hash = 31 * hash + java.util.Objects.hashCode(sessionKey);
        hash = 31 * hash + java.util.Objects.hashCode(maxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(images);
        return hash;
    }
    @Override public String toString() { return "ModelRequest[systemPrompt=" + systemPrompt + ", messages=" + messages + ", tools=" + tools + ", stream=" + stream + ", sessionKey=" + sessionKey + ", maxOutputTokens=" + maxOutputTokens + ", images=" + images + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelRequest.class, "systemPrompt", ModelRequest::systemPrompt), new dev.openallay.value.ValueSchema.Component<>(ModelRequest.class, "messages", ModelRequest::messages), new dev.openallay.value.ValueSchema.Component<>(ModelRequest.class, "tools", ModelRequest::tools), new dev.openallay.value.ValueSchema.Component<>(ModelRequest.class, "stream", ModelRequest::stream), new dev.openallay.value.ValueSchema.Component<>(ModelRequest.class, "sessionKey", ModelRequest::sessionKey), new dev.openallay.value.ValueSchema.Component<>(ModelRequest.class, "maxOutputTokens", ModelRequest::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelRequest.class, "images", ModelRequest::images)), arguments -> new ModelRequest((String) arguments[0], (List) arguments[1], (List) arguments[2], (Boolean) arguments[3], (String) arguments[4], (Integer) arguments[5], (ImagePayloadResolver) arguments[6]));
        }
    }
}
