package dev.openallay.model;

import com.google.gson.JsonObject;
import java.util.Objects;

public record ModelToolDefinition(String name, String description, JsonObject inputSchema) {
    public ModelToolDefinition {
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Model tool name and description are required");
        }
        inputSchema = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(inputSchema, "inputSchema"));
    }

    @Override
    public JsonObject inputSchema() {
        return dev.openallay.json.JsonTrees.copy(inputSchema);
    }
}
