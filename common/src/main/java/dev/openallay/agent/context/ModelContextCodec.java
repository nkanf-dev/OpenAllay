package dev.openallay.agent.context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Strict provider-neutral transcript. Display history is never a source of model messages. */
public final class ModelContextCodec {
    public String encode(List<ModelMessage> messages) {
        JsonObject envelope = new JsonObject();
        JsonArray encoded = new JsonArray();
        for (ModelMessage message : safe(messages)) {
            JsonObject object = new JsonObject();
            object.addProperty("role", message.role().name());
            JsonArray content = new JsonArray();
            for (ModelContent item : message.content()) {
                JsonObject value = new JsonObject();
                switch (item) {
                    case ModelContent.Text text -> {
                        value.addProperty("type", "text");
                        value.addProperty("text", text.text());
                    }
                    case ModelContent.Image image -> {
                        value.addProperty("type", "image");
                        var reference = image.reference();
                        value.addProperty("sha256", reference.sha256());
                        value.addProperty("mimeType", reference.mimeType());
                        value.addProperty("width", reference.width());
                        value.addProperty("height", reference.height());
                        value.addProperty("byteSize", reference.byteSize());
                    }
                    case ModelContent.ToolUse use -> {
                        value.addProperty("type", "tool_use");
                        value.addProperty("id", use.id());
                        value.addProperty("name", use.name());
                        value.add("input", use.input());
                    }
                    case ModelContent.ToolResult result -> {
                        value.addProperty("type", "tool_result");
                        value.addProperty("toolUseId", result.toolUseId());
                        value.add("value", result.value());
                        value.addProperty("error", result.error());
                    }
                    case ModelContent.Reasoning ignored -> throw new IllegalStateException(
                            "private reasoning reached safe model context");
                }
                content.add(value);
            }
            object.add("content", content);
            encoded.add(object);
        }
        envelope.add("messages", encoded);
        return envelope.toString();
    }

    public List<ModelMessage> decode(String json) {
        JsonObject envelope = object(JsonParser.parseString(json));
        fields(envelope, Set.of("messages"));
        ArrayList<ModelMessage> messages = new ArrayList<>();
        for (JsonElement encoded : array(envelope.get("messages"))) {
            JsonObject message = object(encoded);
            fields(message, Set.of("role", "content"));
            ModelRole role = ModelRole.valueOf(text(message, "role"));
            ArrayList<ModelContent> content = new ArrayList<>();
            for (JsonElement raw : array(message.get("content"))) {
                JsonObject item = object(raw);
                switch (text(item, "type")) {
                    case "text" -> {
                        fields(item, Set.of("type", "text"));
                        content.add(new ModelContent.Text(text(item, "text")));
                    }
                    case "image" -> {
                        fields(item, Set.of("type", "sha256", "mimeType", "width", "height", "byteSize"));
                        content.add(new ModelContent.Image(new dev.openallay.model.image.ImageReference(
                                text(item, "sha256"), text(item, "mimeType"),
                                Math.toIntExact(positiveInteger(item, "width")),
                                Math.toIntExact(positiveInteger(item, "height")),
                                positiveInteger(item, "byteSize"))));
                    }
                    case "tool_use" -> {
                        fields(item, Set.of("type", "id", "name", "input"));
                        content.add(new ModelContent.ToolUse(text(item, "id"), text(item, "name"),
                                object(item.get("input"))));
                    }
                    case "tool_result" -> {
                        fields(item, Set.of("type", "toolUseId", "value", "error"));
                        JsonElement error = item.get("error");
                        if (!error.isJsonPrimitive() || !error.getAsJsonPrimitive().isBoolean()) {
                            throw new IllegalArgumentException("model context error flag must be boolean");
                        }
                        content.add(new ModelContent.ToolResult(text(item, "toolUseId"),
                                item.get("value"), error.getAsBoolean()));
                    }
                    default -> throw new IllegalArgumentException("unknown model context content type");
                }
            }
            messages.add(new ModelMessage(role, content));
        }
        ContextStructure.units(messages);
        return List.copyOf(messages);
    }

    /** Excludes provider-private reasoning without altering actual calls, results, or error flags. */
    public static List<ModelMessage> safe(List<ModelMessage> messages) {
        List<ModelMessage> safe = ContextStructure.summarySafe(messages);
        ContextStructure.units(safe);
        return safe;
    }

    private static void fields(JsonObject value, Set<String> expected) {
        if (!value.keySet().equals(expected)) {
            throw new IllegalArgumentException("model context fields do not match the current shape");
        }
    }

    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("model context value must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value) {
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("model context value must be an array");
        }
        return value.getAsJsonArray();
    }

    private static long positiveInteger(JsonObject value, String field) {
        JsonElement number = value.get(field);
        if (number == null || !number.isJsonPrimitive()
                || !number.getAsJsonPrimitive().isNumber()
                || !number.getAsString().matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("model context " + field + " must be a positive integer");
        }
        try {
            return Long.parseLong(number.getAsString());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("model context " + field + " is out of range", invalid);
        }
    }

    private static String text(JsonObject value, String field) {
        JsonElement text = value.get(field);
        if (text == null || !text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("model context " + field + " must be text");
        }
        return text.getAsString();
    }
}
