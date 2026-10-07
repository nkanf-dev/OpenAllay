package dev.openallay.model.openai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import dev.openallay.model.image.ImageReference;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class OpenAiJsonCodec {
    private final Gson gson;

    public OpenAiJsonCodec(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    // Published image-input/request limits, not model token costs.
    // https://platform.openai.com/docs/guides/images-vision
    private static final int MAX_IMAGES = 500;
    // Application decimal-byte guard within the published 50 MB image-input request limit.
    private static final long MAX_REQUEST_BYTES = 50_000_000;

    public String requestBody(ModelConfig config, ModelRequest request) {
        validateImages(request.messages());
        JsonObject root = contextInput(request.systemPrompt(), request.messages(), request.tools(),
                image -> encodeImage(request, image));
        root.addProperty("model", config.model());
        root.addProperty("max_completion_tokens", request.effectiveMaxOutputTokens(config.maxOutputTokens()));
        root.addProperty("stream", request.stream());
        if (config.reasoningEffort() != dev.openallay.model.config.ModelReasoningEffort.AUTO) {
            root.addProperty("reasoning_effort", config.reasoningEffort().encoded());
        }
        String body = gson.toJson(root);
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("OpenAI request exceeds 50,000,000 bytes");
        }
        return body;
    }

    /**
     * Offline native framing. Images use metadata placeholders: no payload is
     * resolved or Base64 counted as text, and the image token cost is unknown.
     */
    public JsonObject contextInput(
            String systemPrompt, List<ModelMessage> inputMessages, List<ModelToolDefinition> inputTools) {
        return contextInput(systemPrompt, inputMessages, inputTools, OpenAiJsonCodec::imagePlaceholder);
    }

    private JsonObject contextInput(
            String systemPrompt,
            List<ModelMessage> inputMessages,
            List<ModelToolDefinition> inputTools,
            Function<ImageReference, JsonObject> imageEncoder) {
        JsonObject root = new JsonObject();
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);
        messages.add(system);
        ProviderToolIds toolIds = ProviderToolIds.forOpenAiChat(inputMessages);
        for (ModelMessage message : inputMessages) {
            encodeMessage(message, messages, toolIds, imageEncoder);
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
        JsonObject root = dev.openallay.json.JsonTrees.parse(json).getAsJsonObject();
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
            ModelMessage message,
            JsonArray output,
            ProviderToolIds toolIds,
            Function<ImageReference, JsonObject> imageEncoder) {
        List<ModelContent.ToolResult> results = dev.openallay.util.Java8Collections.toList(message.content().stream()
                .filter(ModelContent.ToolResult.class::isInstance)
                .map(ModelContent.ToolResult.class::cast));
        if (!results.isEmpty()) {
            for (ModelContent.ToolResult result : results) {
                JsonObject encoded = new JsonObject();
                encoded.addProperty("role", "tool");
                encoded.addProperty("tool_call_id", toolIds.encode(result.toolUseId()));
                encoded.addProperty("content", providerToolResult(result.value()));
                output.add(encoded);
            }
            // Chat Completions tool content supports text, not image_url. Keep the complete
            // reply group first, then project visual tool evidence as provider-only user input.
            JsonArray observations = new JsonArray();
            for (ModelContent.ToolResult result : results) {
                if (result.images().isEmpty()) continue;
                JsonObject label = new JsonObject();
                label.addProperty("type", "text");
                label.addProperty("text", dev.openallay.model.image.ModelImages.observationLabel(result.toolUseId()));
                observations.add(label);
                result.images().forEach(image -> observations.add(imageEncoder.apply(image)));
            }
            if (!(observations.size() == 0)) {
                JsonObject visual = new JsonObject();
                visual.addProperty("role", "user");
                visual.add("content", observations);
                output.add(visual);
            }
            return;
        }

        JsonObject encoded = new JsonObject();
        encoded.addProperty("role", message.role() == ModelRole.USER ? "user" : "assistant");
        boolean multipart = message.inputObservation().isPresent()
                || message.content().stream().anyMatch(ModelContent.Image.class::isInstance);
        JsonArray parts = new JsonArray();
        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        JsonArray toolCalls = new JsonArray();
        for (ModelContent block : message.content()) {
            Objects.requireNonNull(block);
            if (block instanceof ModelContent.Text) {
            ModelContent.Text value = (ModelContent.Text) block;
                if (multipart) {
                    JsonObject part = new JsonObject();
                    part.addProperty("type", "text");
                    part.addProperty("text", value.text());
                    parts.add(part);
                } else {
                    text.append(value.text());
                }
            } else if (block instanceof ModelContent.Image) {
            ModelContent.Image value = (ModelContent.Image) block;
                if (value.originToolUseId() != null) {
                    JsonObject label = new JsonObject();
                    label.addProperty("type", "text");
                    label.addProperty("text", dev.openallay.model.image.ModelImages.observationLabel(value.originToolUseId()));
                    parts.add(label);
                }
                parts.add(imageEncoder.apply(value.reference()));
            } else if (block instanceof ModelContent.Reasoning) {
            ModelContent.Reasoning value = (ModelContent.Reasoning) block;
                reasoning.append(value.text());
            } else if (block instanceof ModelContent.ToolUse) {
            ModelContent.ToolUse value = (ModelContent.ToolUse) block;
                toolCalls.add(encodeToolCall(value, toolIds));
            } else if (block instanceof ModelContent.ToolResult) {
                throw new IllegalStateException();
            } else {
                throw new IncompatibleClassChangeError();
            }
        }
        message.inputObservation().ifPresent(anchor -> {
            JsonObject label = new JsonObject();
            label.addProperty("type", "text");
            label.addProperty("text", dev.openallay.model.image.ModelImages.inputObservationLabel(anchor));
            parts.add(label);
            anchor.image().ifPresent(capture -> parts.add(imageEncoder.apply(capture.image())));
        });
        if (multipart) {
            encoded.add("content", parts);
        } else {
            encoded.addProperty("content", text.length() == 0 ? null : text.toString());
        }
        if (reasoning.length() != 0) {
            encoded.addProperty("reasoning_content", reasoning.toString());
        }
        if (!(toolCalls.size() == 0)) {
            encoded.add("tool_calls", toolCalls);
        }
        output.add(encoded);
    }

    private static void validateImages(List<ModelMessage> messages) {
        int count = 0;
        long encodedBytes = 0;
        for (ImageReference reference : dev.openallay.model.image.ModelImages.occurrences(messages)) {
            if (++count > MAX_IMAGES) {
                throw new IllegalArgumentException("OpenAI request exceeds 500 images");
            }
            if (!reference.mimeType().equals("image/png")
                    && !reference.mimeType().equals("image/jpeg")) {
                throw new IllegalArgumentException("OpenAI image must be PNG or JPEG");
            }
            if (reference.byteSize() > MAX_REQUEST_BYTES) {
                throw new IllegalArgumentException("OpenAI image exceeds request byte limit");
            }
            encodedBytes += 4 * ((reference.byteSize() + 2) / 3);
            if (encodedBytes > MAX_REQUEST_BYTES) {
                throw new IllegalArgumentException("OpenAI image payload exceeds 50,000,000 bytes");
            }
        }
    }

    private static JsonObject encodeImage(ModelRequest request, ImageReference reference) {
        byte[] bytes;
        try {
            bytes = Objects.requireNonNull(request.images().read(reference), "image bytes");
        } catch (IOException failure) {
            throw new UncheckedIOException("OpenAI image payload is unavailable", failure);
        }
        if (bytes.length != reference.byteSize()) {
            throw new IllegalArgumentException("Image payload byte size does not match its reference");
        }
        JsonObject imageUrl = new JsonObject();
        imageUrl.addProperty("url", "data:" + reference.mimeType() + ";base64,"
                + Base64.getEncoder().encodeToString(bytes));
        JsonObject part = new JsonObject();
        part.addProperty("type", "image_url");
        part.add("image_url", imageUrl);
        return part;
    }

    private static JsonObject imagePlaceholder(ImageReference reference) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("media_type", reference.mimeType());
        metadata.addProperty("width", reference.width());
        metadata.addProperty("height", reference.height());
        metadata.addProperty("byte_size", reference.byteSize());
        JsonObject part = new JsonObject();
        part.addProperty("type", "image_url");
        part.add("image_url", metadata);
        return part;
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
                        dev.openallay.json.JsonTrees.parse(requiredString(function, "arguments"))
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
