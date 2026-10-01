package dev.openallay.model;

import java.util.List;

public record ModelRequest(
        String systemPrompt,
        List<ModelMessage> messages,
        List<ModelToolDefinition> tools,
        boolean stream,
        String sessionKey,
        Integer maxOutputTokens) {
    public ModelRequest {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("System prompt must not be blank");
        }
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
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
            String sessionKey) {
        this(systemPrompt, messages, tools, stream, sessionKey, null);
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
