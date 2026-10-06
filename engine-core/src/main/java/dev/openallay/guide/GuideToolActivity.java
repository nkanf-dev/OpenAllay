package dev.openallay.guide;

import com.google.gson.JsonObject;
import java.util.List;

public record GuideToolActivity(
        String invocationId,
        int index,
        String toolId,
        GuideToolStatus status,
        JsonObject invocationArguments,
        GuideToolInvocationView invocation,
        JsonObject normalized,
        List<GuideToolMessage> presentationMessages,
        List<GuideSource> sources) {
    public GuideToolActivity {
        if (invocationId == null || invocationId.isBlank()
                || index < 0 || toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("tool activity identity is invalid");
        }
        java.util.Objects.requireNonNull(status, "status");
        invocationArguments =
                invocationArguments == null ? null : dev.openallay.json.JsonTrees.copy(invocationArguments);
        invocation = java.util.Objects.requireNonNull(invocation, "invocation");
        normalized = normalized == null ? null : dev.openallay.json.JsonTrees.copy(normalized);
        presentationMessages = List.copyOf(presentationMessages);
        sources = List.copyOf(sources);
    }

    public GuideToolActivity(
            String invocationId,
            int index,
            String toolId,
            GuideToolStatus status,
            JsonObject invocationArguments,
            JsonObject normalized,
            List<GuideToolMessage> presentationMessages,
            List<GuideSource> sources) {
        this(
                invocationId,
                index,
                toolId,
                status,
                invocationArguments,
                GuideToolInvocationView.from(toolId, invocationArguments, normalized),
                normalized,
                presentationMessages,
                sources);
    }

    public GuideToolActivity(
            String invocationId,
            int index,
            String toolId,
            GuideToolStatus status,
            JsonObject normalized,
            List<GuideToolMessage> presentationMessages,
            List<GuideSource> sources) {
        this(
                invocationId,
                index,
                toolId,
                status,
                null,
                GuideToolInvocationView.from(toolId, null, normalized),
                normalized,
                presentationMessages,
                sources);
    }

    public GuideToolIntent intent() {
        return GuideToolIntent.from(toolId, invocationArguments, presentationMessages);
    }

    @Override
    public JsonObject invocationArguments() {
        return invocationArguments == null ? null : dev.openallay.json.JsonTrees.copy(invocationArguments);
    }

    @Override
    public JsonObject normalized() {
        return normalized == null ? null : dev.openallay.json.JsonTrees.copy(normalized);
    }
}
