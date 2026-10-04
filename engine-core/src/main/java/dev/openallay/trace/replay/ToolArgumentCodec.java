package dev.openallay.trace.replay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.openallay.tool.ToolResult;
import java.util.Objects;

public final class ToolArgumentCodec {
    private final Gson gson;

    public ToolArgumentCodec(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public <I> ToolResult<I> decode(JsonObject arguments, Class<I> inputType) {
        try {
            if (arguments != null && inputType.isRecord()) {
                var declared = java.util.Arrays.stream(inputType.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .collect(java.util.stream.Collectors.toSet());
                if (!declared.containsAll(arguments.keySet())) {
                    return new ToolResult.Failure<>(
                            "invalid_arguments", "tool arguments contain an undeclared field");
                }
                for (var component : inputType.getRecordComponents()) {
                    var value = arguments.get(component.getName());
                    if (component.getGenericType() instanceof java.lang.reflect.ParameterizedType list
                            && list.getRawType() == java.util.List.class
                            && java.util.Arrays.equals(list.getActualTypeArguments(), new java.lang.reflect.Type[] {String.class})
                            && value != null && !value.isJsonNull()
                            && (!value.isJsonArray() || value.getAsJsonArray().asList().stream().anyMatch(item ->
                                    !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()))) {
                        return new ToolResult.Failure<>(
                                "invalid_arguments", component.getName() + " must be an array of text");
                    }
                    if (component.getType() == String.class && value != null && !value.isJsonNull()
                            && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())) {
                        return new ToolResult.Failure<>(
                                "invalid_arguments", component.getName() + " must be text");
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
