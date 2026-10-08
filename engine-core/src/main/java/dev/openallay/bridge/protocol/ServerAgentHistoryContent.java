package dev.openallay.bridge.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.model.ModelContent;

/** Strict provider-neutral history content carried to a server-hosted model. */
@dev.openallay.value.ValueType(ServerAgentHistoryContent.ValueSchemaProvider.class)
public final class ServerAgentHistoryContent {
    private final Kind kind;
    private final String text;
    private final String toolUseId;
    private final String toolName;
    private final String json;
    private final Boolean error;
    private final dev.openallay.model.image.ImageReference image;
    private final java.util.List<dev.openallay.model.image.ImageReference> images;
    private final String originToolUseId;
    public ServerAgentHistoryContent(Kind kind, String text, String toolUseId, String toolName, String json, Boolean error, dev.openallay.model.image.ImageReference image, java.util.List<dev.openallay.model.image.ImageReference> images, String originToolUseId) {

        java.util.Objects.requireNonNull(kind, "kind");
        if (originToolUseId != null && (kind != Kind.IMAGE || dev.openallay.util.Java8Strings.isBlank(originToolUseId))) {
            throw new IllegalArgumentException("Image origin belongs only to image history content");
        }
        if (kind == Kind.TOOL_RESULT) {
            images = dev.openallay.util.Java8Collections.listCopyOf(images);
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
            case IMAGE: {
                if (image == null || text != null || toolUseId != null || toolName != null
                        || json != null || error != null) {
                    throw new IllegalArgumentException("Malformed image history content");
                }
                break;
            }
            case TEXT: {
                if (text == null || toolUseId != null || toolName != null
                        || json != null || error != null) {
                    throw new IllegalArgumentException("Malformed text history content");
                }
                break;
            }
            case TOOL_USE: {
                if (text != null || blank(toolUseId) || blank(toolName)
                        || blank(json) || error != null
                        || !dev.openallay.json.JsonTrees.parse(json).isJsonObject()) {
                    throw new IllegalArgumentException("Malformed tool-use history content");
                }
                break;
            }
            case TOOL_RESULT: {
                if (text != null || blank(toolUseId) || toolName != null
                        || blank(json) || error == null) {
                    throw new IllegalArgumentException("Malformed tool-result history content");
                }
                dev.openallay.json.JsonTrees.parse(json);
                break;
            }
        }

        this.kind = kind;
        this.text = text;
        this.toolUseId = toolUseId;
        this.toolName = toolName;
        this.json = json;
        this.error = error;
        this.image = image;
        this.images = images;
        this.originToolUseId = originToolUseId;
    }
    public Kind kind() { return kind; }
    public String text() { return text; }
    public String toolUseId() { return toolUseId; }
    public String toolName() { return toolName; }
    public String json() { return json; }
    public Boolean error() { return error; }
    public dev.openallay.model.image.ImageReference image() { return image; }
    public java.util.List<dev.openallay.model.image.ImageReference> images() { return images; }
    public String originToolUseId() { return originToolUseId; }
public enum Kind {
        TEXT,
        IMAGE,
        TOOL_USE,
        TOOL_RESULT
    }
public ServerAgentHistoryContent(
            Kind kind, String text, String toolUseId, String toolName, String json, Boolean error) {
        this(kind, text, toolUseId, toolName, json, error, null,
                kind == Kind.TOOL_RESULT ? dev.openallay.util.Java8Collections.listOf() : null, null);
    }
public ServerAgentHistoryContent(Kind kind, String text, String toolUseId, String toolName,
            String json, Boolean error, dev.openallay.model.image.ImageReference image) {
        this(kind, text, toolUseId, toolName, json, error, image,
                kind == Kind.TOOL_RESULT ? dev.openallay.util.Java8Collections.listOf() : null, null);
    }
public ServerAgentHistoryContent(Kind kind, String text, String toolUseId, String toolName,
            String json, Boolean error, dev.openallay.model.image.ImageReference image,
            java.util.List<dev.openallay.model.image.ImageReference> images) {
        this(kind, text, toolUseId, toolName, json, error, image, images, null);
    }
public static ServerAgentHistoryContent from(ModelContent content) {
        java.util.Objects.requireNonNull(content);
        ModelContent.requireKnown(content);
        if (content instanceof ModelContent.Image) {
            ModelContent.Image value = (ModelContent.Image) content;
            return new ServerAgentHistoryContent(
                    Kind.IMAGE, null, null, null, null, null, value.reference(), null, value.originToolUseId());
        } else if (content instanceof ModelContent.Text) {
            ModelContent.Text value = (ModelContent.Text) content;
            return new ServerAgentHistoryContent(
                    Kind.TEXT, value.text(), null, null, null, null);
        } else if (content instanceof ModelContent.ToolUse) {
            ModelContent.ToolUse value = (ModelContent.ToolUse) content;
            return new ServerAgentHistoryContent(
                    Kind.TOOL_USE, null, value.id(), value.name(), value.input().toString(), null);
        } else if (content instanceof ModelContent.ToolResult) {
            ModelContent.ToolResult value = (ModelContent.ToolResult) content;
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
        switch (kind) {
            case IMAGE: return new ModelContent.Image(image, originToolUseId);
            case TEXT: return new ModelContent.Text(text);
            case TOOL_USE: return new ModelContent.ToolUse(
                    toolUseId, toolName, dev.openallay.json.JsonTrees.parse(json).getAsJsonObject());
            case TOOL_RESULT: return new ModelContent.ToolResult(
                    toolUseId, dev.openallay.json.JsonTrees.parse(json), error, images);
            default: throw new IncompatibleClassChangeError();
        }
    }
private static boolean blank(String value) {
        return value == null || dev.openallay.util.Java8Strings.isBlank(value);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerAgentHistoryContent)) return false;
        ServerAgentHistoryContent that = (ServerAgentHistoryContent) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(text, that.text) && java.util.Objects.equals(toolUseId, that.toolUseId) && java.util.Objects.equals(toolName, that.toolName) && java.util.Objects.equals(json, that.json) && java.util.Objects.equals(error, that.error) && java.util.Objects.equals(image, that.image) && java.util.Objects.equals(images, that.images) && java.util.Objects.equals(originToolUseId, that.originToolUseId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(toolUseId);
        hash = 31 * hash + java.util.Objects.hashCode(toolName);
        hash = 31 * hash + java.util.Objects.hashCode(json);
        hash = 31 * hash + java.util.Objects.hashCode(error);
        hash = 31 * hash + java.util.Objects.hashCode(image);
        hash = 31 * hash + java.util.Objects.hashCode(images);
        hash = 31 * hash + java.util.Objects.hashCode(originToolUseId);
        return hash;
    }
    @Override public String toString() { return "ServerAgentHistoryContent[kind=" + kind + ", text=" + text + ", toolUseId=" + toolUseId + ", toolName=" + toolName + ", json=" + json + ", error=" + error + ", image=" + image + ", images=" + images + ", originToolUseId=" + originToolUseId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerAgentHistoryContent> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerAgentHistoryContent.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerAgentHistoryContent>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryContent.class, "kind", ServerAgentHistoryContent::kind), new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryContent.class, "text", ServerAgentHistoryContent::text), new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryContent.class, "toolUseId", ServerAgentHistoryContent::toolUseId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryContent.class, "toolName", ServerAgentHistoryContent::toolName), new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryContent.class, "json", ServerAgentHistoryContent::json), new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryContent.class, "error", ServerAgentHistoryContent::error), new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryContent.class, "image", ServerAgentHistoryContent::image), new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryContent.class, "images", ServerAgentHistoryContent::images), new dev.openallay.value.ValueSchema.Component<>(ServerAgentHistoryContent.class, "originToolUseId", ServerAgentHistoryContent::originToolUseId)), arguments -> new ServerAgentHistoryContent((Kind) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (Boolean) arguments[5], (dev.openallay.model.image.ImageReference) arguments[6], (java.util.List) arguments[7], (String) arguments[8]));
        }
    }
}
