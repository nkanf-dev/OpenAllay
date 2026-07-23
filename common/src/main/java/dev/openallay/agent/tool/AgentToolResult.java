package dev.openallay.agent.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record AgentToolResult(String toolId, JsonObject normalized, boolean failure) {
    public AgentToolResult {
        normalized = normalized.deepCopy();
    }

    @Override
    public JsonObject normalized() {
        return normalized.deepCopy();
    }

    /** Compact provider-facing value; full normalized JSON remains available to UI and traces. */
    public JsonElement modelValue() {
        return new com.google.gson.JsonPrimitive(ModelToolTextRenderer.render(normalized));
    }
}
