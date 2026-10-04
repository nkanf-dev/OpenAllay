package dev.openallay.agent.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Objects;

/** Authoritative normalized result with a reusable, permission-independent model projection. */
public final class AgentToolResult {
    private final String toolId;
    private final JsonObject normalized;
    private final boolean failure;
    private final java.util.List<dev.openallay.model.image.ImageReference> images;
    private final transient ModelToolResultProjection.Prepared prepared;
    private final transient dev.openallay.tool.ModelResultSource source;

    public AgentToolResult(String toolId, JsonObject normalized, boolean failure) {
        this(toolId, normalized, failure, null);
    }

    public AgentToolResult(String toolId, JsonObject normalized, boolean failure,
            dev.openallay.tool.ModelResultSource source) {
        this(toolId, normalized, failure, source, java.util.List.of());
    }

    public AgentToolResult(String toolId, JsonObject normalized, boolean failure,
            dev.openallay.tool.ModelResultSource source,
            java.util.List<dev.openallay.model.image.ImageReference> images) {
        this.images = java.util.List.copyOf(images);
        dev.openallay.model.image.ModelImages.unique(this.images);
        if (failure && !this.images.isEmpty()) {
            throw new IllegalArgumentException("Failed tool results cannot publish images");
        }
        this.toolId = toolId;
        this.normalized = Objects.requireNonNull(normalized, "normalized").deepCopy();
        this.failure = failure;
        this.prepared = ModelToolResultProjection.prepare(this.normalized);
        this.source = failure ? null : source;
    }

    public String toolId() { return toolId; }
    public JsonObject normalized() { return normalized.deepCopy(); }
    public boolean failure() { return failure; }
    public java.util.List<dev.openallay.model.image.ImageReference> images() { return images; }

    /** Initial transport view, not proof that a provider request fits its token budget. */
    public JsonElement modelValue() {
        return modelValue(dev.openallay.tool.result.JsonResultProjection.DEFAULT_MAXIMUM_UTF8_BYTES);
    }

    /** Actual request fit is decided by the agent's model tokenizer, not by this byte envelope. */
    public JsonElement modelValue(int maximumUtf8Bytes) {
        return source == null ? prepared.project(maximumUtf8Bytes) : source.project(maximumUtf8Bytes);
    }

    /** Search ceiling of the producer's chosen view, not canonical storage capacity. */
    public int projectionSizeUpperBound() {
        return source == null ? prepared.projectionSizeUpperBound() : source.projectionSizeUpperBound();
    }

    public int preferredSizeUtf8Bytes() {
        return source == null ? prepared.projectionSizeUpperBound() : source.preferredSizeUtf8Bytes();
    }

    /** Active-context view only. The original successful transcript remains unchanged. */
    public static JsonElement boundedModelValue(JsonElement value, int maximumUtf8Bytes) {
        return ModelToolResultProjection.boundedValue(value, maximumUtf8Bytes);
    }

    @Override public boolean equals(Object other) {
        return other instanceof AgentToolResult result && Objects.equals(toolId, result.toolId)
                && normalized.equals(result.normalized) && failure == result.failure && images.equals(result.images);
    }
    @Override public int hashCode() { return Objects.hash(toolId, normalized, failure, images); }
    @Override public String toString() {
        return "AgentToolResult[toolId=" + toolId + ", normalized=" + normalized + ", failure=" + failure + ", images=" + images + "]";
    }
}
