package dev.openallay.bridge.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Set;

public final class BridgeJsonCodec {
    private static final Map<Class<?>, Set<String>> FIELDS = dev.openallay.util.Java8Collections.mapOfEntries(dev.openallay.util.Java8Collections.entry(CapabilityPayload.class, dev.openallay.util.Java8Collections.setOf("remoteTools", "serverModel", "serverContextWindowTokens", "serverMaxOutputTokens", "serverPromptAndToolTokens", "serverCanonicalModelId", "serverImageInputCapability", "serverImageInputCapabilitySource")), dev.openallay.util.Java8Collections.entry(RemoteToolCallPayload.class, dev.openallay.util.Java8Collections.setOf("correlationId", "sessionId", "toolId", "argumentsJson")), dev.openallay.util.Java8Collections.entry(RemoteToolResultChunkPayload.class, dev.openallay.util.Java8Collections.setOf("correlationId", "index", "total", "contentHash", "base64Data")), dev.openallay.util.Java8Collections.entry(RemoteCancelPayload.class, dev.openallay.util.Java8Collections.setOf("correlationId")), dev.openallay.util.Java8Collections.entry(RemoteToolRequestClosePayload.class, dev.openallay.util.Java8Collections.setOf("requestId")), dev.openallay.util.Java8Collections.entry(ServerAgentRequestPayload.class, dev.openallay.util.Java8Collections.setOf("requestId", "sessionId", "question", "stream", "history", "clientToolIds", "skillDocuments", "userInput", "imageAttachments")), dev.openallay.util.Java8Collections.entry(ClientToolCallPayload.class, dev.openallay.util.Java8Collections.setOf("requestId", "invocationId", "sessionId", "toolId", "argumentsJson")), dev.openallay.util.Java8Collections.entry(ClientToolResultChunkPayload.class, dev.openallay.util.Java8Collections.setOf("requestId", "invocationId", "index", "total", "contentHash", "base64Data")), dev.openallay.util.Java8Collections.entry(ClientToolCancelPayload.class, dev.openallay.util.Java8Collections.setOf("requestId", "invocationId")), dev.openallay.util.Java8Collections.entry(ToolExecutionMessage.class, dev.openallay.util.Java8Collections.setOf("result", "imageAttachments")), dev.openallay.util.Java8Collections.entry(ServerAgentRequestChunkPayload.class, dev.openallay.util.Java8Collections.setOf("requestId", "index", "total", "contentHash", "base64Data")), dev.openallay.util.Java8Collections.entry(ServerAgentCancelPayload.class, dev.openallay.util.Java8Collections.setOf("requestId")), dev.openallay.util.Java8Collections.entry(ServerAgentSteerPayload.class, dev.openallay.util.Java8Collections.setOf("requestId", "messageId", "operation", "message", "imageAttachments")), dev.openallay.util.Java8Collections.entry(ServerAgentSteerChunkPayload.class, dev.openallay.util.Java8Collections.setOf("requestId", "messageId", "index", "total", "contentHash", "base64Data")), dev.openallay.util.Java8Collections.entry(ServerAgentEventPayload.class, dev.openallay.util.Java8Collections.setOf("requestId", "eventType", "eventJson", "terminal")), dev.openallay.util.Java8Collections.entry(ServerAgentEventChunkPayload.class, dev.openallay.util.Java8Collections.setOf("requestId", "eventId", "index", "total", "contentHash", "base64Data")));

    private final Gson gson;

    public BridgeJsonCodec() {
        this(dev.openallay.json.EngineJson.create());
    }

    public BridgeJsonCodec(Gson gson) {
        this.gson = dev.openallay.json.EngineJson.withInstant(gson);
    }

    public String encode(Object payload) {
        if (!FIELDS.containsKey(payload.getClass())) {
            throw new IllegalArgumentException("Unsupported bridge payload " + payload.getClass().getName());
        }
        if (payload instanceof ToolExecutionMessage message) {
            // Keep explicit nulls in normalized JSON. Reflective Gson serialization would
            // drop them and could turn a malformed optional field into an absent field.
            JsonObject object = new JsonObject();
            object.add("result", message.result());
            object.add("imageAttachments", gson.toJsonTree(message.imageAttachments()));
            return object.toString();
        }
        JsonObject encoded = gson.toJsonTree(payload).getAsJsonObject();
        if (payload instanceof ServerAgentSteerPayload steer) {
            encoded.add("message", steer.message() == null ? com.google.gson.JsonNull.INSTANCE
                    : encodeHistoryMessage(gson, steer.message()));
        } else if (payload instanceof ServerAgentRequestPayload request) {
            com.google.gson.JsonArray history = new com.google.gson.JsonArray();
            for (ServerAgentHistoryMessage message : request.history()) history.add(encodeHistoryMessage(gson, message));
            encoded.add("history", history);
            encoded.add("userInput", encodeHistoryMessage(gson, request.userInput()));
        }
        return encoded.toString();
    }

    /** Current typed IMAGE history shape includes an explicit nullable occurrence origin. */
    public static JsonObject encodeHistoryMessage(Gson gson, ServerAgentHistoryMessage message) {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("role", message.role().name());
        encoded.add("inputObservation", dev.openallay.world.ClientObservationAnchorJson.encode(message.inputObservation()));
        com.google.gson.JsonArray content = new com.google.gson.JsonArray();
        for (ServerAgentHistoryContent block : message.content()) {
            JsonObject value = gson.toJsonTree(block).getAsJsonObject();
            if (block.kind() == ServerAgentHistoryContent.Kind.IMAGE && block.originToolUseId() == null) {
                value.add("originToolUseId", com.google.gson.JsonNull.INSTANCE);
            }
            content.add(value);
        }
        encoded.add("content", content);
        return encoded;
    }

    public <T> T decode(String json, Class<T> type) {
        Set<String> expected = FIELDS.get(type);
        if (expected == null) {
            throw new IllegalArgumentException("Unsupported bridge payload " + type.getName());
        }
        if (type == ServerAgentRequestChunkPayload.class || type == ServerAgentSteerChunkPayload.class
                || type == ClientToolResultChunkPayload.class) {
            requireEncodedEnvelope(json, BridgeProtocol.MAX_REQUEST_CHUNK_JSON_BYTES);
        } else if (type == ServerAgentRequestPayload.class || type == ServerAgentSteerPayload.class
                || type == ToolExecutionMessage.class) {
            requireEncodedEnvelope(json, BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
        }
        rejectDuplicateFields(json);
        JsonElement parsed = dev.openallay.json.JsonTrees.parse(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Bridge payload must be a JSON object");
        }
        JsonObject object = parsed.getAsJsonObject();
        if (!dev.openallay.json.JsonTrees.keys(object).equals(expected)) {
            Set<String> missing = new java.util.TreeSet<>(expected);
            missing.removeAll(dev.openallay.json.JsonTrees.keys(object));
            Set<String> extra = new java.util.TreeSet<>(dev.openallay.json.JsonTrees.keys(object));
            extra.removeAll(expected);
            throw new IllegalArgumentException(
                    "Bridge payload schema mismatch; missing=" + missing + ", extra=" + extra);
        }
        if (type == ServerAgentRequestChunkPayload.class) {
            requireText(object.get("requestId"));
            requireInteger(object.get("index"));
            requireInteger(object.get("total"));
            requireText(object.get("contentHash"));
            requireText(object.get("base64Data"));
        }
        if (type == ServerAgentSteerChunkPayload.class) {
            requireText(object.get("requestId"));
            requireText(object.get("messageId"));
            requireInteger(object.get("index"));
            requireInteger(object.get("total"));
            requireText(object.get("contentHash"));
            requireText(object.get("base64Data"));
        }
        if (type == ClientToolResultChunkPayload.class) {
            requireText(object.get("requestId"));
            requireText(object.get("invocationId"));
            requireInteger(object.get("index"));
            requireInteger(object.get("total"));
            requireText(object.get("contentHash"));
            requireText(object.get("base64Data"));
        }
        if (type == ToolExecutionMessage.class) {
            if (!object.get("result").isJsonObject()) {
                throw new IllegalArgumentException("Tool result must be an object");
            }
            JsonElement attachments = object.get("imageAttachments");
            if (!attachments.isJsonArray()) {
                throw new IllegalArgumentException("Tool image attachments must be an array");
            }
            for (JsonElement item : attachments.getAsJsonArray()) {
                JsonObject attachment = exactObject(item, dev.openallay.util.Java8Collections.setOf("reference", "base64Data"));
                validateImageReference(attachment.get("reference"));
                requireText(attachment.get("base64Data"));
            }
        }
        if (type == ServerAgentSteerPayload.class) {
            requireText(object.get("requestId"));
            requireText(object.get("messageId"));
            requireText(object.get("operation"));
            if (!object.get("message").isJsonNull()) validateHistoryMessage(object.get("message"));
            JsonElement attachments = object.get("imageAttachments");
            if (!attachments.isJsonArray()) throw new IllegalArgumentException("Steer attachments must be an array");
            for (JsonElement item : attachments.getAsJsonArray()) {
                JsonObject attachment = exactObject(item, dev.openallay.util.Java8Collections.setOf("reference", "base64Data"));
                validateImageReference(attachment.get("reference"));
                requireText(attachment.get("base64Data"));
            }
        }
        if (type == CapabilityPayload.class) {
            requireText(object.get("serverImageInputCapability"));
            if (!dev.openallay.util.Java8Collections.setOf("SUPPORTED", "UNSUPPORTED", "UNKNOWN").contains(
                    object.get("serverImageInputCapability").getAsString())) {
                throw new IllegalArgumentException("Unknown server image input capability");
            }
            requireText(object.get("serverImageInputCapabilitySource"));
        }
        if (type == ServerAgentRequestPayload.class) {
            requireText(object.get("requestId"));
            requireText(object.get("sessionId"));
            requireText(object.get("question"));
            requireBoolean(object.get("stream"));
            JsonElement history = object.get("history");
            if (history == null || !history.isJsonArray()) {
                throw new IllegalArgumentException("Server Agent history must be an array");
            }
            for (JsonElement item : history.getAsJsonArray()) validateHistoryMessage(item);
            validateHistoryMessage(object.get("userInput"));
            JsonElement attachments = object.get("imageAttachments");
            if (attachments == null || !attachments.isJsonArray()) {
                throw new IllegalArgumentException("Server Agent image attachments must be an array");
            }
            for (JsonElement item : attachments.getAsJsonArray()) {
                JsonObject attachment = exactObject(item, dev.openallay.util.Java8Collections.setOf("reference", "base64Data"));
                validateImageReference(attachment.get("reference"));
                requireText(attachment.get("base64Data"));
            }
            JsonElement clientToolIds = object.get("clientToolIds");
            if (clientToolIds == null || !clientToolIds.isJsonArray()) {
                throw new IllegalArgumentException("Server Agent client Tool IDs must be an array");
            }
            for (JsonElement toolId : clientToolIds.getAsJsonArray()) {
                if (!toolId.isJsonPrimitive() || !toolId.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException(
                            "Server Agent client Tool IDs must contain strings");
                }
            }
            validateSkillManifest(object.get("skillDocuments"));
        }
        try {
            T value = gson.fromJson(object, type);
            if (value == null) {
                throw new IllegalArgumentException("Bridge payload decoded to null");
            }
            return value;
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            // Gson wraps record-constructor validation failures. Keep the public wire boundary's
            // malformed-payload contract stable without exposing reflected constructor arguments.
            throw new IllegalArgumentException("Bridge payload values do not match the current shape", malformed);
        }
    }

    private static void requireEncodedEnvelope(String json, int maximumBytes) {
        java.util.Objects.requireNonNull(json, "json");
        if (json.length() > maximumBytes) throw new IllegalArgumentException("Bridge request envelope is too large");
        long bytes = 0;
        for (int index = 0; index < json.length(); index++) {
            char value = json.charAt(index);
            if (value <= 0x7f) bytes++;
            else if (value <= 0x7ff) bytes += 2;
            else if (Character.isHighSurrogate(value) && index + 1 < json.length()
                    && Character.isLowSurrogate(json.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else bytes += Character.isSurrogate(value) ? 1 : 3;
            if (bytes > maximumBytes) throw new IllegalArgumentException("Bridge request envelope is too large");
        }
    }

    /** Gson tokenizes JSON; this scan only rejects repeated object field names before tree decoding. */
    public static void rejectDuplicateFields(String json) {
        try (JsonReader reader = dev.openallay.json.JsonReaders.strict(new java.io.StringReader(json))) {

            java.util.ArrayDeque<Set<String>> objects = new java.util.ArrayDeque<>();
            do {
                switch (reader.peek()) {
                    case BEGIN_OBJECT -> { reader.beginObject(); objects.push(new java.util.HashSet<>()); }
                    case BEGIN_ARRAY -> { reader.beginArray(); objects.push(dev.openallay.util.Java8Collections.setOf()); }
                    case NAME -> {
                        if (!objects.peek().add(reader.nextName())) {
                            throw new IllegalArgumentException("Duplicate bridge JSON field");
                        }
                    }
                    case END_OBJECT -> { reader.endObject(); objects.pop(); }
                    case END_ARRAY -> { reader.endArray(); objects.pop(); }
                    default -> reader.skipValue();
                }
            } while (!objects.isEmpty());
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing bridge JSON");
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Malformed bridge JSON", malformed);
        }
    }

    /** Shared by every current typed history wire boundary. No paths, URLs or encoded bytes. */
    public static void validateHistoryMessage(JsonElement item) {
        JsonObject message = exactObject(item, dev.openallay.util.Java8Collections.setOf("role", "content", "inputObservation"));
        dev.openallay.world.ClientObservationAnchorJson.decode(message.get("inputObservation"));
        requireText(message.get("role"));
        if (!dev.openallay.util.Java8Collections.setOf("USER", "ASSISTANT").contains(message.get("role").getAsString())) {
            throw new IllegalArgumentException("Unknown Server Agent history role");
        }
        JsonElement content = message.get("content");
        if (content == null || !content.isJsonArray()) {
            throw new IllegalArgumentException("Server Agent history content must be an array");
        }
        for (JsonElement block : content.getAsJsonArray()) {
            if (!block.isJsonObject()) {
                throw new IllegalArgumentException("Server Agent history content schema mismatch");
            }
            JsonObject value = block.getAsJsonObject();
            requireText(value.get("kind"));
            Set<String> fields = switch (value.get("kind").getAsString()) {
                case "TEXT" -> dev.openallay.util.Java8Collections.setOf("kind", "text");
                case "IMAGE" -> dev.openallay.util.Java8Collections.setOf("kind", "image", "originToolUseId");
                case "TOOL_USE" -> dev.openallay.util.Java8Collections.setOf("kind", "toolUseId", "toolName", "json");
                case "TOOL_RESULT" -> dev.openallay.util.Java8Collections.setOf("kind", "toolUseId", "json", "error", "images");
                default -> throw new IllegalArgumentException("Unknown Server Agent history content kind");
            };
            exactObject(value, fields);
            for (String field : fields) {
                switch (field) {
                    case "image" -> validateImageReference(value.get(field));
                    case "originToolUseId" -> {
                        if (!value.get(field).isJsonNull()) requireText(value.get(field));
                    }
                    case "images" -> {
                        JsonElement images = value.get(field);
                        if (images == null || !images.isJsonArray()) {
                            throw new IllegalArgumentException("Tool result images must be an array");
                        }
                        for (JsonElement image : images.getAsJsonArray()) validateImageReference(image);
                    }
                    case "error" -> requireBoolean(value.get(field));
                    default -> requireText(value.get(field));
                }
            }
        }
    }

    public static void validateImageReference(JsonElement element) {
        JsonObject image = exactObject(element, dev.openallay.util.Java8Collections.setOf("sha256", "mimeType", "width", "height", "byteSize"));
        requireText(image.get("sha256"));
        requireText(image.get("mimeType"));
        requireInteger(image.get("width"));
        requireInteger(image.get("height"));
        requireLong(image.get("byteSize"));
    }

    private static void requireBoolean(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Bridge metadata field must be boolean");
        }
    }

    private static void requireLong(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("[0-9]+")) {
            throw new IllegalArgumentException("Bridge metadata field must be a nonnegative integer");
        }
        try {
            Long.parseLong(value.getAsString());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Bridge metadata integer is out of range", invalid);
        }
    }

    private static void validateSkillManifest(JsonElement element) {
        JsonObject manifest = exactObject(element, dev.openallay.util.Java8Collections.setOf("documents"));
        JsonElement documents = manifest.get("documents");
        if (!documents.isJsonArray()) {
            throw new IllegalArgumentException("Skill documents must be an array");
        }
        for (JsonElement item : documents.getAsJsonArray()) {
            JsonObject document = exactObject(item, dev.openallay.util.Java8Collections.setOf("name", "document", "source", "fingerprint", "length", "chunks", "availableReferences", "description"));
            for (String field : dev.openallay.util.Java8Collections.listOf("name", "document", "source", "fingerprint", "description")) {
                requireText(document.get(field));
            }
            requireInteger(document.get("length"));
            JsonElement references = document.get("availableReferences");
            if (!references.isJsonArray()) {
                throw new IllegalArgumentException("Skill references must be an array");
            }
            references.getAsJsonArray().forEach(BridgeJsonCodec::requireText);
            JsonElement chunks = document.get("chunks");
            if (!chunks.isJsonArray()) {
                throw new IllegalArgumentException("Skill chunks must be an array");
            }
            for (JsonElement raw : chunks.getAsJsonArray()) {
                JsonObject chunk = exactObject(raw, dev.openallay.util.Java8Collections.setOf("offset", "end", "fingerprint"));
                requireInteger(chunk.get("offset"));
                requireInteger(chunk.get("end"));
                requireText(chunk.get("fingerprint"));
            }
        }
    }

    private static JsonObject exactObject(JsonElement element, Set<String> fields) {
        if (element == null || !element.isJsonObject()
                || !dev.openallay.json.JsonTrees.keys(element.getAsJsonObject()).equals(fields)) {
            throw new IllegalArgumentException("Bridge metadata schema mismatch");
        }
        return element.getAsJsonObject();
    }

    private static void requireText(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Skill catalog metadata field must be text");
        }
    }

    private static void requireInteger(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("[0-9]+")) {
            throw new IllegalArgumentException("Skill catalog metadata field must be a nonnegative integer");
        }
        try {
            Integer.parseInt(value.getAsString());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Skill catalog metadata integer is out of range", invalid);
        }
    }
}
