package dev.openallay.trace.replay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.openallay.tool.ToolResult;
import java.util.Objects;

public final class ToolArgumentCodec {
    private final Gson gson;

    public ToolArgumentCodec(Gson gson) {
        this.gson = dev.openallay.json.EngineJson.withInstant(Objects.requireNonNull(gson, "gson"));
    }

    private record Component(String name, java.lang.reflect.Type genericType, Class<?> rawType) {}
    private static java.util.List<Component> components(Class<?> owner) {
        java.util.List<Component> result = new java.util.ArrayList<>();
        if (dev.openallay.value.ValueSchemas.supports(owner)) {
            for (dev.openallay.value.ValueSchema.Component<?> component : dev.openallay.value.ValueSchemas.of(owner).components()) {
                result.add(new Component(component.name(), component.genericType(), component.rawType()));
            }
        } else {
            for (java.lang.reflect.RecordComponent component : owner.getRecordComponents()) {
                result.add(new Component(component.getName(), component.getGenericType(), component.getType()));
            }
        }
        return result;
    }

    public <I> ToolResult<I> decode(JsonObject arguments, Class<I> inputType) {
        try {
            if (arguments != null && (inputType.isRecord() || dev.openallay.value.ValueSchemas.supports(inputType))) {
                var components = components(inputType);
                var declared = components.stream()
                        .map(Component::name)
                        .collect(java.util.stream.Collectors.toSet());
                if (!declared.containsAll(dev.openallay.json.JsonTrees.keys(arguments))) {
                    return new ToolResult.Failure<>(
                            "invalid_arguments", "tool arguments contain an undeclared field");
                }
                for (var component : components) {
                    var value = arguments.get(component.name());
                    if (component.genericType() instanceof java.lang.reflect.ParameterizedType list
                            && list.getRawType() == java.util.List.class
                            && java.util.Arrays.equals(list.getActualTypeArguments(), new java.lang.reflect.Type[] {String.class})
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
                    message == null || message.isBlank()
                            ? "tool arguments do not match the declared input type"
                            : message);
        }
    }
}
