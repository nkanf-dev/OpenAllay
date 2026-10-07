package dev.openallay.model;

import com.google.gson.JsonObject;
import java.util.Objects;

@dev.openallay.value.ValueType(ModelToolDefinition.ValueSchemaProvider.class)
public final class ModelToolDefinition {
    private final String name;
    private final String description;
    private final JsonObject inputSchema;
    public ModelToolDefinition(String name, String description, JsonObject inputSchema) {

        if (name == null || dev.openallay.util.Java8Strings.isBlank(name) || description == null || dev.openallay.util.Java8Strings.isBlank(description)) {
            throw new IllegalArgumentException("Model tool name and description are required");
        }
        inputSchema = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(inputSchema, "inputSchema"));

        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }
    public String name() { return name; }
    public String description() { return description; }

    public JsonObject inputSchema() {
        return dev.openallay.json.JsonTrees.copy(inputSchema);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelToolDefinition)) return false;
        ModelToolDefinition that = (ModelToolDefinition) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(inputSchema, that.inputSchema);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(inputSchema);
        return hash;
    }
    @Override public String toString() { return "ModelToolDefinition[name=" + name + ", description=" + description + ", inputSchema=" + inputSchema + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelToolDefinition> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelToolDefinition.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelToolDefinition>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelToolDefinition.class, "name", ModelToolDefinition::name), new dev.openallay.value.ValueSchema.Component<>(ModelToolDefinition.class, "description", ModelToolDefinition::description), new dev.openallay.value.ValueSchema.Component<>(ModelToolDefinition.class, "inputSchema", ModelToolDefinition::inputSchema)), arguments -> new ModelToolDefinition((String) arguments[0], (String) arguments[1], (JsonObject) arguments[2]));
        }
    }
}
