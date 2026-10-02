package dev.openallay.model;

import dev.openallay.model.image.ImagePayloadResolver;
import java.util.List;
import java.util.Objects;

/** Provider input plus request-only scoped asset access; resolvers are not transcript data. */
public record ModelRequest(
        String systemPrompt,
        List<ModelMessage> messages,
        List<ModelToolDefinition> tools,
        boolean stream,
        String sessionKey,
        Integer maxOutputTokens,
        ImagePayloadResolver images) {
    public ModelRequest {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("System prompt must not be blank");
        }
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        Objects.requireNonNull(images, "images");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("At least one model message is required");
        }
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalArgumentException("Model request sessionKey must not be blank");
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("Model request maxOutputTokens must be positive");
        }
    }

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

    /** A request may reduce, but never increase, the configured output-token limit. */
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
}
