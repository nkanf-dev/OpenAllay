package dev.openallay.bridge.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.model.ModelContent;

/** Strict provider-neutral history content carried to a server-hosted model. */
public record ServerAgentHistoryContent(
        Kind kind,
        String text,
        String toolUseId,
        String toolName,
        String json,
        Boolean error,
        dev.openallay.model.image.ImageReference image,
        java.util.List<dev.openallay.model.image.ImageReference> images,
        String originToolUseId) {
    public enum Kind {
        TEXT,
        IMAGE,
        TOOL_USE,
        TOOL_RESULT
    }

    public ServerAgentHistoryContent {
        java.util.Objects.requireNonNull(kind, "kind");
        if (originToolUseId != null && (kind != Kind.IMAGE || originToolUseId.isBlank())) {
            throw new IllegalArgumentException("Image origin belongs only to image history content");
        }
        if (kind == Kind.TOOL_RESULT) {
            images = java.util.List.copyOf(images);
            dev.openallay.model.image.ModelImages.unique(images);
            if (Boolean.TRUE.equals(error) && !images.isEmpty()) {
                throw new IllegalArgumentException("Failed tool results cannot publish images");
            }
        } else if (images != null) {
            throw new IllegalArgumentException("Tool images belong only to tool-result history content");
        }
        if (kind != Kind.IMAGE && image != null) {
            throw new IllegalArgumentException("Image metadata belongs only to image history content");
        }
        switch (kind) {
            case IMAGE -> {
                if (image == null || text != null || toolUseId != null || toolName != null
                        || json != null || error != null) {
                    throw new IllegalArgumentException("Malformed image history content");
                }
            }
            case TEXT -> {
                if (text == null || toolUseId != null || toolName != null
                        || json != null || error != null) {
                    throw new IllegalArgumentException("Malformed text history content");
                }
            }
            case TOOL_USE -> {
                if (text != null || blank(toolUseId) || blank(toolName)
                        || blank(json) || error != null
                        || !dev.openallay.json.JsonTrees.parse(json).isJsonObject()) {
                    throw new IllegalArgumentException("Malformed tool-use history content");
                }
            }
            case TOOL_RESULT -> {
                if (text != null || blank(toolUseId) || toolName != null
                        || blank(json) || error == null) {
                    throw new IllegalArgumentException("Malformed tool-result history content");
                }
                dev.openallay.json.JsonTrees.parse(json);
            }
        }
    }

    public ServerAgentHistoryContent(
            Kind kind, String text, String toolUseId, String toolName, String json, Boolean error) {
        this(kind, text, toolUseId, toolName, json, error, null,
                kind == Kind.TOOL_RESULT ? java.util.List.of() : null, null);
    }

    public ServerAgentHistoryContent(Kind kind, String text, String toolUseId, String toolName,
            String json, Boolean error, dev.openallay.model.image.ImageReference image) {
        this(kind, text, toolUseId, toolName, json, error, image,
                kind == Kind.TOOL_RESULT ? java.util.List.of() : null, null);
    }

    public ServerAgentHistoryContent(Kind kind, String text, String toolUseId, String toolName,
            String json, Boolean error, dev.openallay.model.image.ImageReference image,
            java.util.List<dev.openallay.model.image.ImageReference> images) {
        this(kind, text, toolUseId, toolName, json, error, image, images, null);
    }

    public static ServerAgentHistoryContent from(ModelContent content) {
        java.util.Objects.requireNonNull(content);
        if (content instanceof ModelContent.Image value) {
            return new ServerAgentHistoryContent(
                    Kind.IMAGE, null, null, null, null, null, value.reference(), null, value.originToolUseId());
        } else if (content instanceof ModelContent.Text value) {
            return new ServerAgentHistoryContent(
                    Kind.TEXT, value.text(), null, null, null, null);
        } else if (content instanceof ModelContent.ToolUse value) {
            return new ServerAgentHistoryContent(
                    Kind.TOOL_USE, null, value.id(), value.name(), value.input().toString(), null);
        } else if (content instanceof ModelContent.ToolResult value) {
            return new ServerAgentHistoryContent(
                    Kind.TOOL_RESULT, null, value.toolUseId(), null,
                    value.value().toString(), value.error(), null, value.images());
        } else if (content instanceof ModelContent.Reasoning) {
            throw new IllegalArgumentException(
                    "Reasoning content cannot enter durable bridge history");
        } else {
            throw new IncompatibleClassChangeError();
        }
    }

    public ModelContent toModelContent() {
        return switch (kind) {
            case IMAGE -> new ModelContent.Image(image, originToolUseId);
            case TEXT -> new ModelContent.Text(text);
            case TOOL_USE -> new ModelContent.ToolUse(
                    toolUseId, toolName, dev.openallay.json.JsonTrees.parse(json).getAsJsonObject());
            case TOOL_RESULT -> new ModelContent.ToolResult(
                    toolUseId, dev.openallay.json.JsonTrees.parse(json), error, images);
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
