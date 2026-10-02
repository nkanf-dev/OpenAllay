package dev.openallay.model.openai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.ProviderToolIds;
import dev.openallay.model.config.ModelConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class OpenAiJsonCodec {
    private final Gson gson;

    public OpenAiJsonCodec(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public String requestBody(ModelConfig config, ModelRequest request) {
        JsonObject root = contextInput(request.systemPrompt(), request.messages(), request.tools());
        root.addProperty("model", config.model());
        root.addProperty("max_completion_tokens", request.effectiveMaxOutputTokens(config.maxOutputTokens()));
        root.addProperty("stream", request.stream());
        if (config.reasoningEffort() != dev.openallay.model.config.ModelReasoningEffort.AUTO) {
            root.addProperty("reasoning_effort", config.reasoningEffort().encoded());
        }
        return gson.toJson(root);
    }

    /** Provider-native input shape shared by HTTP encoding and offline token budgeting. */
    public JsonObject contextInput(
            String systemPrompt, List<ModelMessage> inputMessages, List<ModelToolDefinition> inputTools) {
        JsonObject root = new JsonObject();
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);
        messages.add(system);
        ProviderToolIds toolIds = ProviderToolIds.forOpenAiChat(inputMessages);
        for (ModelMessage message : inputMessages) {
            encodeMessage(message, messages, toolIds);
        }
        root.add("messages", messages);
        if (!inputTools.isEmpty()) {
            JsonArray tools = new JsonArray();
            for (ModelToolDefinition tool : inputTools) {
                JsonObject function = new JsonObject();
                function.addProperty("name", tool.name());
                function.addProperty("description", tool.description());
                function.add("parameters", tool.inputSchema());
                JsonObject encoded = new JsonObject();
                encoded.addProperty("type", "function");
                encoded.add("function", function);
                tools.add(encoded);
            }
            root.add("tools", tools);
        }
        return root;
    }

    public ModelTurn parseTurn(String json, Consumer<ModelEvent> events) {
        try {
            return decodeTurn(json, events);
        } catch (RuntimeException failure) {
            dev.openallay.model.http.ModelTransportDiagnostics.openAiDecodeFailure(false, json, failure);
            throw failure;
        }
    }

    private ModelTurn decodeTurn(String json, Consumer<ModelEvent> events) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String model = requiredString(root, "model");
        JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
        String stopReason = requiredString(choice, "finish_reason");
        JsonObject message = choice.getAsJsonObject("message");
        List<ModelContent> content = decodeAssistant(message, events);
        JsonObject usageObject = optionalObject(root, "usage");
        ModelUsage usage = parseUsage(usageObject);
        if (usageObject != null) events.accept(new ModelEvent.UsageUpdate(usage));
        events.accept(new ModelEvent.MessageComplete(stopReason));
        return new ModelTurn("openai_chat", model, content, stopReason, usage);
    }

    private void encodeMessage(
            ModelMessage message, JsonArray output, ProviderToolIds toolIds) {
        List<ModelContent.ToolResult> results = message.content().stream()
                .filter(ModelContent.ToolResult.class::isInstance)
                .map(ModelContent.ToolResult.class::cast)
                .toList();
        if (!results.isEmpty()) {
            for (ModelContent.ToolResult result : results) {
                JsonObject encoded = new JsonObject();
                encoded.addProperty("role", "tool");
                encoded.addProperty("tool_call_id", toolIds.encode(result.toolUseId()));
                encoded.addProperty("content", providerToolResult(result.value()));
                output.add(encoded);
            }
            return;
        }

        JsonObject encoded = new JsonObject();
        encoded.addProperty("role", message.role() == ModelRole.USER ? "user" : "assistant");
        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        JsonArray toolCalls = new JsonArray();
        for (ModelContent block : message.content()) {
            switch (block) {
                case ModelContent.Text value -> text.append(value.text());
                case ModelContent.Reasoning value -> reasoning.append(value.text());
                case ModelContent.ToolUse value -> toolCalls.add(encodeToolCall(value, toolIds));
                case ModelContent.ToolResult ignored -> throw new IllegalStateException();
            }
        }
        encoded.addProperty("content", text.isEmpty() ? null : text.toString());
        if (!reasoning.isEmpty()) {
            encoded.addProperty("reasoning_content", reasoning.toString());
        }
        if (!toolCalls.isEmpty()) {
            encoded.add("tool_calls", toolCalls);
        }
        output.add(encoded);
    }

    private JsonObject encodeToolCall(ModelContent.ToolUse tool, ProviderToolIds toolIds) {
        JsonObject function = new JsonObject();
        function.addProperty("name", tool.name());
        function.addProperty("arguments", gson.toJson(tool.input()));
        JsonObject result = new JsonObject();
        result.addProperty("id", toolIds.encode(tool.id()));
        result.addProperty("type", "function");
        result.add("function", function);
        return result;
    }

    private String providerToolResult(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : gson.toJson(value);
    }

    private List<ModelContent> decodeAssistant(JsonObject message, Consumer<ModelEvent> events) {
        List<ModelContent> content = new ArrayList<>();
        if (message.has("content") && !message.get("content").isJsonNull()) {
            ModelContent.Text text = new ModelContent.Text(message.get("content").getAsString());
            content.add(text);
            events.accept(new ModelEvent.TextDelta(text.text()));
        }
        if (message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()) {
            ModelContent.Reasoning reasoning =
                    new ModelContent.Reasoning(message.get("reasoning_content").getAsString(), null);
            content.add(reasoning);
            events.accept(new ModelEvent.ReasoningDelta(reasoning.text()));
        }
        if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
            for (JsonElement element : message.getAsJsonArray("tool_calls")) {
                JsonObject call = element.getAsJsonObject();
                JsonObject function = call.getAsJsonObject("function");
                ModelContent.ToolUse tool = new ModelContent.ToolUse(
                        requiredString(call, "id"),
                        requiredString(function, "name"),
                        JsonParser.parseString(requiredString(function, "arguments"))
                                .getAsJsonObject());
                content.add(tool);
                events.accept(new ModelEvent.ToolUseComplete(tool.id(), tool.name(), tool.input()));
            }
        }
        return content;
    }

    static boolean hasUsageCounts(JsonObject object) {
        return object != null && object.has("prompt_tokens") && !object.get("prompt_tokens").isJsonNull()
                && object.has("completion_tokens") && !object.get("completion_tokens").isJsonNull();
    }

    static ModelUsage parseUsage(JsonObject object) {
        if (object == null) return ModelUsage.empty();
        JsonObject details = optionalObject(object, "prompt_tokens_details");
        return ModelUsage.openAi(
                value(object, "prompt_tokens"), hasCount(object, "prompt_tokens"),
                value(object, "completion_tokens"), hasCount(object, "completion_tokens"),
                details == null ? 0 : value(details, "cached_tokens"), hasCount(details, "cached_tokens"));
    }

    private static boolean hasCount(JsonObject object, String field) {
        return object != null && object.has(field) && !object.get(field).isJsonNull();
    }

    private static JsonObject optionalObject(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? null : value.getAsJsonObject();
    }

    private static long value(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsBigDecimal().longValueExact()
                : 0;
    }

    private static String requiredString(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing OpenAI response field: " + field);
        }
        return object.get(field).getAsString();
    }
}
