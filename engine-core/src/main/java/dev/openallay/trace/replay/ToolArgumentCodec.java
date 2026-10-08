package dev.openallay.trace.replay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.openallay.tool.ToolResult;
import java.util.Objects;
import java.lang.reflect.ParameterizedType;
import dev.openallay.util.Java8Strings;
import dev.openallay.value.RecordMetadata;

public final class ToolArgumentCodec {
    private final Gson gson;

    public ToolArgumentCodec(Gson gson) {
        this.gson = dev.openallay.json.EngineJson.withInstant(Objects.requireNonNull(gson, "gson"));
    }

    private static final class Component {
        private final String name;
        private final java.lang.reflect.Type genericType;
        private final Class<?> rawType;
        private Component(String name, java.lang.reflect.Type genericType, Class<?> rawType) {
            this.name = name; this.genericType = genericType; this.rawType = rawType;
        }
        String name() { return name; }
        java.lang.reflect.Type genericType() { return genericType; }
        Class<?> rawType() { return rawType; }
    }
    private static java.util.List<Component> components(Class<?> owner) {
        java.util.List<Component> result = new java.util.ArrayList<>();
        if (dev.openallay.value.ValueSchemas.supports(owner)) {
            for (dev.openallay.value.ValueSchema.Component<?> component : dev.openallay.value.ValueSchemas.of(owner).components()) {
                result.add(new Component(component.name(), component.genericType(), component.rawType()));
            }
        } else {
            for (RecordMetadata.Component component : RecordMetadata.components(owner)) {
                result.add(new Component(component.name(), component.genericType(), component.rawType()));
            }
        }
        return result;
    }

    public <I> ToolResult<I> decode(JsonObject arguments, Class<I> inputType) {
        try {
            boolean componentInput = dev.openallay.value.ValueSchemas.supports(inputType) || RecordMetadata.isRecord(inputType);
            if (!componentInput) {
                // Keep every existing schema-supported scalar/array/JSON input lane. Only
                // undeclared arbitrary POJOs must not bypass the closed tool schema contract.
                try { new dev.openallay.agent.tool.ToolSchemaGenerator().generate(inputType); }
                catch (IllegalArgumentException unsupported) {
                    return new ToolResult.Failure<>(
                            "invalid_arguments", "tool input type has no declared tool schema");
                }
            }
            if (arguments != null && componentInput) {
                java.util.List<Component> components = components(inputType);
                java.util.Set<String> declared = components.stream()
                        .map(Component::name)
                        .collect(java.util.stream.Collectors.toSet());
                if (!declared.containsAll(dev.openallay.json.JsonTrees.keys(arguments))) {
                    return new ToolResult.Failure<>(
                            "invalid_arguments", "tool arguments contain an undeclared field");
                }
                for (Component component : components) {
                    com.google.gson.JsonElement value = arguments.get(component.name());
                    if (component.genericType() instanceof ParameterizedType
                            && ((ParameterizedType) component.genericType()).getRawType() == java.util.List.class
                            && java.util.Arrays.equals(((ParameterizedType) component.genericType()).getActualTypeArguments(), new java.lang.reflect.Type[] {String.class})
                            && value != null && !value.isJsonNull()
                            && (!value.isJsonArray() || dev.openallay.json.JsonReaders.elements(value.getAsJsonArray()).stream().anyMatch(item ->
                                    !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()))) {
                        return new ToolResult.Failure<>(
                                "invalid_arguments", component.name() + " must be an array of text");
                    }
                    if (component.rawType() == String.class && value != null && !value.isJsonNull()
                            && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())) {
                        return new ToolResult.Failure<>(
                                "invalid_arguments", component.name() + " must be text");
                    }
                }
            }
            I input = gson.fromJson(arguments, inputType);
            if (input == null) {
                return new ToolResult.Failure<>(
                        "invalid_arguments", "tool arguments decoded to null");
            }
            return new ToolResult.Success<>(input);
        } catch (JsonParseException | IllegalArgumentException exception) {
            String message = exception.getMessage();
            return new ToolResult.Failure<>(
                    "invalid_arguments",
                    message == null || Java8Strings.isBlank(message)
                            ? "tool arguments do not match the declared input type"
                            : message);
        }
    }
}
