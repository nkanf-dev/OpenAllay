package dev.openallay.bridge.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Set;

public final class BridgeJsonCodec {
    private static final Map<Class<?>, Set<String>> FIELDS = Map.ofEntries(
            Map.entry(CapabilityPayload.class, Set.of(
                    "remoteTools", "serverModel",
                    "serverContextWindowTokens", "serverMaxOutputTokens",
                    "serverPromptAndToolTokens", "serverCanonicalModelId")),
            Map.entry(RemoteToolCallPayload.class,
                    Set.of("correlationId", "sessionId", "toolId", "argumentsJson")),
            Map.entry(RemoteToolResultChunkPayload.class,
                    Set.of("correlationId", "index", "total", "contentHash", "base64Data")),
            Map.entry(RemoteCancelPayload.class, Set.of("correlationId")),
            Map.entry(RemoteToolRequestClosePayload.class, Set.of("requestId")),
            Map.entry(ServerAgentRequestPayload.class,
                    Set.of(
                            "requestId", "sessionId", "question", "stream",
                            "history", "clientToolIds", "skillDocuments")),
            Map.entry(ClientToolCallPayload.class,
                    Set.of(
                            "requestId", "invocationId", "sessionId", "toolId",
                            "argumentsJson")),
            Map.entry(ClientToolResultChunkPayload.class,
                    Set.of(
                            "requestId", "invocationId", "index", "total",
                            "contentHash", "base64Data")),
            Map.entry(ClientToolCancelPayload.class,
                    Set.of("requestId", "invocationId")),
            Map.entry(ServerAgentRequestChunkPayload.class,
                    Set.of("requestId", "index", "total", "contentHash", "base64Data")),
            Map.entry(ServerAgentCancelPayload.class, Set.of("requestId")),
            Map.entry(ServerAgentEventPayload.class,
                    Set.of("requestId", "eventType", "eventJson", "terminal")),
            Map.entry(ServerAgentEventChunkPayload.class,
                    Set.of(
                            "requestId", "eventId", "index", "total",
                            "contentHash", "base64Data")));

    private final Gson gson;

    public BridgeJsonCodec() {
        this(new Gson());
    }

    public BridgeJsonCodec(Gson gson) {
        this.gson = gson;
    }

    public String encode(Object payload) {
        if (!FIELDS.containsKey(payload.getClass())) {
            throw new IllegalArgumentException("Unsupported bridge payload " + payload.getClass().getName());
        }
        return gson.toJson(payload);
    }

    public <T> T decode(String json, Class<T> type) {
        Set<String> expected = FIELDS.get(type);
        if (expected == null) {
            throw new IllegalArgumentException("Unsupported bridge payload " + type.getName());
        }
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Bridge payload must be a JSON object");
        }
        JsonObject object = parsed.getAsJsonObject();
        if (!object.keySet().equals(expected)) {
            Set<String> missing = new java.util.TreeSet<>(expected);
            missing.removeAll(object.keySet());
            Set<String> extra = new java.util.TreeSet<>(object.keySet());
            extra.removeAll(expected);
            throw new IllegalArgumentException(
                    "Bridge payload schema mismatch; missing=" + missing + ", extra=" + extra);
        }
        if (type == ServerAgentRequestPayload.class) {
            JsonElement history = object.get("history");
            if (history == null || !history.isJsonArray()) {
                throw new IllegalArgumentException("Server Agent history must be an array");
            }
            for (JsonElement item : history.getAsJsonArray()) {
                if (!item.isJsonObject()
                        || !item.getAsJsonObject().keySet().equals(Set.of("role", "content"))) {
                    throw new IllegalArgumentException("Server Agent history schema mismatch");
                }
                JsonElement content = item.getAsJsonObject().get("content");
                if (content == null || !content.isJsonArray()) {
                    throw new IllegalArgumentException("Server Agent history content must be an array");
                }
                for (JsonElement block : content.getAsJsonArray()) {
                    if (!block.isJsonObject()) {
                        throw new IllegalArgumentException(
                                "Server Agent history content schema mismatch");
                    }
                    JsonObject contentObject = block.getAsJsonObject();
                    JsonElement kind = contentObject.get("kind");
                    Set<String> contentFields = kind == null ? Set.of() : switch (kind.getAsString()) {
                        case "TEXT" -> Set.of("kind", "text");
                        case "TOOL_USE" -> Set.of(
                                "kind", "toolUseId", "toolName", "json");
                        case "TOOL_RESULT" -> Set.of(
                                "kind", "toolUseId", "json", "error");
                        default -> Set.of();
                    };
                    if (!contentObject.keySet().equals(contentFields)) {
                        throw new IllegalArgumentException(
                                "Server Agent history content schema mismatch");
                    }
                }
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

    private static void validateSkillManifest(JsonElement element) {
        JsonObject manifest = exactObject(element, Set.of("documents"));
        JsonElement documents = manifest.get("documents");
        if (!documents.isJsonArray()) {
            throw new IllegalArgumentException("Skill documents must be an array");
        }
        for (JsonElement item : documents.getAsJsonArray()) {
            JsonObject document = exactObject(item, Set.of("name", "document", "source", "fingerprint",
                    "length", "chunks", "availableReferences", "description"));
            for (String field : java.util.List.of("name", "document", "source", "fingerprint", "description")) {
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
                JsonObject chunk = exactObject(raw, Set.of("offset", "end", "fingerprint"));
                requireInteger(chunk.get("offset"));
                requireInteger(chunk.get("end"));
                requireText(chunk.get("fingerprint"));
            }
        }
    }

    private static JsonObject exactObject(JsonElement element, Set<String> fields) {
        if (element == null || !element.isJsonObject()
                || !element.getAsJsonObject().keySet().equals(fields)) {
            throw new IllegalArgumentException("Skill catalog metadata schema mismatch");
        }
        return element.getAsJsonObject();
    }

    private static void requireText(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Skill catalog metadata field must be text");
        }
    }

    private static void requireInteger(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
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
