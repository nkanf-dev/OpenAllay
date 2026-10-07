package dev.openallay.model.anthropic;

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

public final class AnthropicJsonCodec {
    private final Gson gson;

    public AnthropicJsonCodec(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    // Direct Claude API vision facts, checked 2026-10-02:
    // https://platform.claude.com/docs/en/build-with-claude/vision
    // "100 per request ... for models with a 200k-token context window";
    // "600 per request ... for all other models". Use the API-wide maximum here:
    // config.contextWindowTokens is a user-overridable budget, not published model capability.
    // For >20 images the API applies a stricter dimension limit; the documented
    // 2000px cross-platform recommendation is not an exact direct-API threshold.
    private static final int MAX_IMAGES = 600;
    // "10 MB (base64-encoded) when using the Claude API directly."
    // Partner platforms have different limits; do not infer them from protocol.
    private static final long MAX_ENCODED_IMAGE_BYTES = 10_000_000;
    private static final int MAX_IMAGE_DIMENSION = 8_000;
    // Application decimal-byte guard within the published 32 MB standard request limit.
    private static final long MAX_REQUEST_BYTES = 32_000_000;

    public String requestBody(ModelConfig config, ModelRequest request) {
        validateImages(request.messages());
        JsonObject root = contextInput(request.systemPrompt(), request.messages(), request.tools(),
                image -> encodeImage(request, image));
        root.addProperty("model", config.model());
        root.addProperty("max_tokens", request.effectiveMaxOutputTokens(config.maxOutputTokens()));
        root.addProperty("stream", request.stream());
        if (config.reasoningEffort() != dev.openallay.model.config.ModelReasoningEffort.AUTO) {
            JsonObject outputConfig = new JsonObject();
            outputConfig.addProperty("effort", config.reasoningEffort().encoded());
            root.add("output_config", outputConfig);
        }
        String body = gson.toJson(root);
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Anthropic request exceeds 32,000,000 bytes");
        }
        return body;
    }

    /**
     * Offline native framing with image metadata only. It never reads a payload
     * or counts binary/Base64 as text; image token costs remain unknown.
     */
    public JsonObject contextInput(
            String systemPrompt, List<ModelMessage> inputMessages, List<ModelToolDefinition> inputTools) {
        return contextInput(systemPrompt, inputMessages, inputTools, AnthropicJsonCodec::imagePlaceholder);
    }

    private JsonObject contextInput(
            String systemPrompt,
            List<ModelMessage> inputMessages,
            List<ModelToolDefinition> inputTools,
            Function<ImageReference, JsonObject> imageEncoder) {
        JsonObject root = new JsonObject();
        root.addProperty("system", systemPrompt);
        JsonArray messages = new JsonArray();
        ProviderToolIds toolIds = ProviderToolIds.forAnthropicMessages(inputMessages);
        for (ModelMessage message : inputMessages) {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("role", message.role() == ModelRole.USER ? "user" : "assistant");
            JsonArray content = new JsonArray();
            for (ModelContent block : message.content()) {
                if (block instanceof ModelContent.Image && ((ModelContent.Image) block).originToolUseId() != null) {
                    ModelContent.Image image = (ModelContent.Image) block;
                    JsonObject label = new JsonObject();
                    label.addProperty("type", "text");
                    label.addProperty("text", dev.openallay.model.image.ModelImages.observationLabel(image.originToolUseId()));
                    content.add(label);
                }
                content.add(encodeContent(block, toolIds, imageEncoder));
            }
            message.inputObservation().ifPresent(anchor -> {
                JsonObject label = new JsonObject();
                label.addProperty("type", "text");
                label.addProperty("text", dev.openallay.model.image.ModelImages.inputObservationLabel(anchor));
                content.add(label);
                anchor.image().ifPresent(capture -> content.add(imageEncoder.apply(capture.image())));
            });
            encoded.add("content", content);
            messages.add(encoded);
        }
        root.add("messages", messages);
        if (!inputTools.isEmpty()) {
            JsonArray tools = new JsonArray();
            for (ModelToolDefinition tool : inputTools) {
                JsonObject encoded = new JsonObject();
                encoded.addProperty("name", tool.name());
                encoded.addProperty("description", tool.description());
                encoded.add("input_schema", tool.inputSchema());
                tools.add(encoded);
            }
            root.add("tools", tools);
        }
        return root;
    }

    public ModelTurn parseTurn(String json, Consumer<ModelEvent> events) {
        JsonObject root = dev.openallay.json.JsonTrees.parse(json).getAsJsonObject();
        if (root.has("error") && !root.get("error").isJsonNull()) {
            throw new IllegalArgumentException("Anthropic response contains an error object");
        }
        String model = requiredString(root, "model");
        String stopReason = requiredString(root, "stop_reason");
        List<ModelContent> content = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("content")) {
            ModelContent block = decodeContent(element.getAsJsonObject());
            content.add(block);
            emitCompleteBlock(block, events);
        }
        JsonElement usageValue = root.get("usage");
        JsonObject usageObject = usageValue == null || usageValue.isJsonNull()
                ? null : usageValue.getAsJsonObject();
        ModelUsage usage = parseUsage(usageObject);
        if (usageObject != null) events.accept(new ModelEvent.UsageUpdate(usage));
        events.accept(new ModelEvent.MessageComplete(stopReason));
        return new ModelTurn("anthropic_messages", model, content, stopReason, usage);
    }

    static boolean hasCount(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull();
    }

    public ModelUsage parseUsage(JsonObject object) {
        if (object == null) {
            return ModelUsage.empty();
        }
        return ModelUsage.anthropic(
                longValue(object, "input_tokens"), hasCount(object, "input_tokens"),
                longValue(object, "output_tokens"), hasCount(object, "output_tokens"),
                longValue(object, "cache_read_input_tokens"), hasCount(object, "cache_read_input_tokens"),
                longValue(object, "cache_creation_input_tokens"), hasCount(object, "cache_creation_input_tokens"));
    }

    private JsonObject encodeContent(
            ModelContent block,
            ProviderToolIds toolIds,
            Function<ImageReference, JsonObject> imageEncoder) {
        JsonObject encoded = new JsonObject();
        Objects.requireNonNull(block);
        if (block instanceof ModelContent.Text) {
            ModelContent.Text text = (ModelContent.Text) block;
            encoded.addProperty("type", "text");
            encoded.addProperty("text", text.text());
        } else if (block instanceof ModelContent.Image) {
            ModelContent.Image image = (ModelContent.Image) block;
            return imageEncoder.apply(image.reference());
        } else if (block instanceof ModelContent.Reasoning) {
            ModelContent.Reasoning reasoning = (ModelContent.Reasoning) block;
            encoded.addProperty("type", "thinking");
            encoded.addProperty("thinking", reasoning.text());
            if (reasoning.signature() != null) {
                encoded.addProperty("signature", reasoning.signature());
            }
        } else if (block instanceof ModelContent.ToolUse) {
            ModelContent.ToolUse toolUse = (ModelContent.ToolUse) block;
            encoded.addProperty("type", "tool_use");
            encoded.addProperty("id", toolIds.encode(toolUse.id()));
            encoded.addProperty("name", toolUse.name());
            encoded.add("input", toolUse.input());
        } else if (block instanceof ModelContent.ToolResult) {
            ModelContent.ToolResult result = (ModelContent.ToolResult) block;
            encoded.addProperty("type", "tool_result");
            encoded.addProperty("tool_use_id", toolIds.encode(result.toolUseId()));
            if (result.images().isEmpty()) {
                encoded.addProperty("content", providerToolResult(result.value()));
            } else {
                JsonArray content = new JsonArray();
                JsonObject text = new JsonObject();
                text.addProperty("type", "text");
                text.addProperty("text", providerToolResult(result.value()));
                content.add(text);
                result.images().forEach(image -> content.add(imageEncoder.apply(image)));
                encoded.add("content", content);
            }
            encoded.addProperty("is_error", result.error());
        } else {
            throw new IncompatibleClassChangeError();
        }
        return encoded;
    }

    private static void validateImages(List<ModelMessage> messages) {
        int count = 0;
        long encodedBytes = 0;
        for (ImageReference reference : dev.openallay.model.image.ModelImages.occurrences(messages)) {
            if (++count > MAX_IMAGES) {
                throw new IllegalArgumentException("Anthropic request exceeds 600 images");
            }
            if (!reference.mimeType().equals("image/png")
                    && !reference.mimeType().equals("image/jpeg")) {
                throw new IllegalArgumentException("Anthropic image must be PNG or JPEG");
            }
            // This early raw-byte bound also prevents long overflow before Base64 length math.
            if (reference.byteSize() > MAX_ENCODED_IMAGE_BYTES) {
                throw new IllegalArgumentException("Anthropic image exceeds 10,000,000 Base64 bytes");
            }
            long base64Bytes = 4 * ((reference.byteSize() + 2) / 3);
            if (base64Bytes > MAX_ENCODED_IMAGE_BYTES) {
                throw new IllegalArgumentException("Anthropic image exceeds 10,000,000 Base64 bytes");
            }
            if (reference.width() > MAX_IMAGE_DIMENSION || reference.height() > MAX_IMAGE_DIMENSION) {
                throw new IllegalArgumentException("Anthropic image dimension exceeds 8,000 pixels");
            }
            encodedBytes += base64Bytes;
            if (encodedBytes > MAX_REQUEST_BYTES) {
                throw new IllegalArgumentException("Anthropic image payload exceeds 32,000,000 bytes");
            }
        }
    }

    private static JsonObject encodeImage(ModelRequest request, ImageReference reference) {
        byte[] bytes;
        try {
            bytes = Objects.requireNonNull(request.images().read(reference), "image bytes");
        } catch (IOException failure) {
            throw new UncheckedIOException("Anthropic image payload is unavailable", failure);
        }
        if (bytes.length != reference.byteSize()) {
            throw new IllegalArgumentException("Image payload byte size does not match its reference");
        }
        JsonObject source = new JsonObject();
        source.addProperty("type", "base64");
        source.addProperty("media_type", reference.mimeType());
        source.addProperty("data", Base64.getEncoder().encodeToString(bytes));
        JsonObject part = new JsonObject();
        part.addProperty("type", "image");
        part.add("source", source);
        return part;
    }

    private static JsonObject imagePlaceholder(ImageReference reference) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("media_type", reference.mimeType());
        metadata.addProperty("width", reference.width());
        metadata.addProperty("height", reference.height());
        metadata.addProperty("byte_size", reference.byteSize());
        JsonObject part = new JsonObject();
        part.addProperty("type", "image");
        part.add("source", metadata);
        return part;
    }

    private ModelContent decodeContent(JsonObject object) {
        switch (requiredString(object, "type")) {
            case "text": return new ModelContent.Text(requiredString(object, "text"));
            case "thinking": return new ModelContent.Reasoning(
                    requiredString(object, "thinking"),
                    object.has("signature") ? object.get("signature").getAsString() : null);
            case "tool_use": return new ModelContent.ToolUse(
                    requiredString(object, "id"),
                    requiredString(object, "name"),
                    object.getAsJsonObject("input"));
            default: throw new IllegalArgumentException(
                    "Unsupported Anthropic content type: " + requiredString(object, "type"));
        }
    }

    private String providerToolResult(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : gson.toJson(value);
    }

    private static void emitCompleteBlock(ModelContent block, Consumer<ModelEvent> events) {
        Objects.requireNonNull(block);
        if (block instanceof ModelContent.Text) {
            ModelContent.Text text = (ModelContent.Text) block;
            events.accept(new ModelEvent.TextDelta(text.text()));
        } else if (block instanceof ModelContent.Reasoning) {
            ModelContent.Reasoning reasoning = (ModelContent.Reasoning) block;
            events.accept(new ModelEvent.ReasoningDelta(reasoning.text()));
        } else if (block instanceof ModelContent.ToolUse) {
            ModelContent.ToolUse toolUse = (ModelContent.ToolUse) block;
            events.accept(new ModelEvent.ToolUseComplete(
                    toolUse.id(), toolUse.name(), toolUse.input()));
        } else if (block instanceof ModelContent.Image) {
            throw new IllegalArgumentException("Anthropic image blocks are input-only");
        } else if (block instanceof ModelContent.ToolResult) {
            // Tool results do not emit complete provider blocks.
        } else {
            throw new IncompatibleClassChangeError();
        }
    }

    private static String requiredString(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing Anthropic response field: " + field);
        }
        return object.get(field).getAsString();
    }

    private static long longValue(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsBigDecimal().longValueExact()
                : 0;
    }
}
