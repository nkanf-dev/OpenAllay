package dev.openallay.guide.history;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ContextCheckpointCodec;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.guide.GuideSource;
import dev.openallay.guide.GuideUsageSnapshot;
import dev.openallay.model.ModelUsage;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolInvocationView;
import dev.openallay.guide.GuideToolMessageCodec;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.semantic.SemanticDocumentCodec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict codecs for player-visible durable projections and derived checkpoints. */
public final class GuideHistoryCodec {
    private final ContextCheckpointCodec checkpoints = new ContextCheckpointCodec();
    private final SemanticDocumentCodec semanticDocuments = new SemanticDocumentCodec();
    private static final Set<String> USER_FIELDS = Set.of("type", "ordinal", "messageId", "text");
    private static final Set<String> ASSISTANT_FIELDS =
            Set.of("type", "ordinal", "text", "semantic", "streaming", "sources");
    private static final Set<String> TOOL_FIELDS = Set.of(
            "type", "ordinal", "invocationId", "index", "toolId", "status",
            "invocation", "presentationMessages", "sources");
    private static final Set<String> TOOL_INVOCATION_FIELDS =
            Set.of("handles", "modules");
    private static final Set<String> SOURCE_FIELDS =
            Set.of("toolId", "evidence", "lastCapturedAt");
    private static final Set<String> EVIDENCE_FIELDS = Set.of(
            "authority", "completeness", "capturedAt", "sourceId", "provenance",
            "gameVersion", "loader", "details");
    private static final Set<String> SERVER_SELECTION_FIELDS = Set.of("kind");
    private static final Set<String> CLIENT_SELECTION_FIELDS = Set.of("kind", "profileId");

    public String encodeModelSelection(GuideModelSelection selection) {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("kind", selection.kind().name());
        if (selection.kind() == GuideModelSelection.Kind.CLIENT) {
            encoded.addProperty("profileId", selection.profileId());
        }
        return encoded.toString();
    }

    public GuideModelSelection decodeModelSelection(String json) {
        JsonObject encoded = object(dev.openallay.json.JsonTrees.parse(json), "model selection");
        GuideModelSelection.Kind kind = enumValue(
                GuideModelSelection.Kind.class,
                string(encoded, "kind"),
                "model selection kind");
        return switch (kind) {
            case CLIENT -> {
                requireFields(encoded, CLIENT_SELECTION_FIELDS, "client model selection");
                yield GuideModelSelection.client(string(encoded, "profileId"));
            }
            case SERVER -> {
                requireFields(encoded, SERVER_SELECTION_FIELDS, "server model selection");
                yield GuideModelSelection.server();
            }
        };
    }

    /** Exact current shape; no missing presence flags are silently interpreted as zero. */
    public String encodeModelUsage(ModelUsage usage) {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("inputTokens", usage.inputTokens());
        encoded.addProperty("outputTokens", usage.outputTokens());
        encoded.addProperty("cacheReadTokens", usage.cacheReadTokens());
        encoded.addProperty("cacheWriteTokens", usage.cacheWriteTokens());
        encoded.addProperty("uncachedInputTokens", usage.uncachedInputTokens());
        encoded.addProperty("inputKnown", usage.inputKnown());
        encoded.addProperty("outputKnown", usage.outputKnown());
        encoded.addProperty("cacheReadKnown", usage.cacheReadKnown());
        encoded.addProperty("cacheWriteKnown", usage.cacheWriteKnown());
        encoded.addProperty("uncachedInputKnown", usage.uncachedInputKnown());
        return encoded.toString();
    }

    public ModelUsage decodeModelUsage(String json) {
        JsonObject encoded = object(dev.openallay.json.JsonTrees.parse(json), "model usage");
        requireFields(encoded, Set.of("inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens",
                "uncachedInputTokens", "inputKnown", "outputKnown", "cacheReadKnown", "cacheWriteKnown",
                "uncachedInputKnown"), "model usage");
        return new ModelUsage(longInteger(encoded, "inputTokens"), longInteger(encoded, "outputTokens"),
                longInteger(encoded, "cacheReadTokens"), longInteger(encoded, "cacheWriteTokens"),
                longInteger(encoded, "uncachedInputTokens"), bool(encoded, "inputKnown"),
                bool(encoded, "outputKnown"), bool(encoded, "cacheReadKnown"), bool(encoded, "cacheWriteKnown"),
                bool(encoded, "uncachedInputKnown"));
    }

    public String encodeUsageProjection(GuideUsageSnapshot usage) {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("inputTokens", usage.inputTokens());
        encoded.addProperty("outputTokens", usage.outputTokens());
        encoded.addProperty("cacheReadTokens", usage.cacheReadTokens());
        encoded.addProperty("cacheWriteTokens", usage.cacheWriteTokens());
        encoded.addProperty("actualCalls", usage.actualCalls());
        encoded.addProperty("reportedCalls", usage.reportedCalls());
        encoded.addProperty("incomplete", usage.incomplete());
        encoded.addProperty("cacheIncomplete", usage.cacheIncomplete());
        encoded.addProperty("estimatedUsd", usage.estimatedUsd());
        encoded.addProperty("costIncomplete", usage.costIncomplete());
        return encoded.toString();
    }

    public GuideUsageSnapshot decodeUsageProjection(String json) {
        JsonObject encoded = object(dev.openallay.json.JsonTrees.parse(json), "usage projection");
        requireFields(encoded, Set.of("inputTokens", "outputTokens", "cacheReadTokens", "cacheWriteTokens",
                "actualCalls", "reportedCalls", "incomplete", "cacheIncomplete", "estimatedUsd",
                "costIncomplete"), "usage projection");
        JsonElement price = encoded.get("estimatedUsd");
        if (!price.isJsonNull() && (!price.isJsonPrimitive() || !price.getAsJsonPrimitive().isNumber())) {
            throw new IllegalArgumentException("estimatedUsd must be a decimal or null");
        }
        return new GuideUsageSnapshot(longInteger(encoded, "inputTokens"), longInteger(encoded, "outputTokens"),
                longInteger(encoded, "cacheReadTokens"), longInteger(encoded, "cacheWriteTokens"),
                integer(encoded, "actualCalls"), integer(encoded, "reportedCalls"), bool(encoded, "incomplete"),
                bool(encoded, "cacheIncomplete"), price.isJsonNull() ? null : price.getAsBigDecimal(),
                bool(encoded, "costIncomplete"));
    }

    private static long longInteger(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException(field + " must be an integer", failure);
        }
    }

    public String encodeCheckpoint(ContextCheckpoint checkpoint) {
        return checkpoints.encode(checkpoint);
    }

    public ContextCheckpoint decodeCheckpoint(String json) {
        return checkpoints.decode(json);
    }

    public String encodeTimeline(List<GuideTimelineEntry> timeline) {
        JsonArray encoded = new JsonArray();
        for (GuideTimelineEntry entry : timeline) {
            encoded.add(encodeEntryObject(entry));
        }
        return encoded.toString();
    }

    public String encodeEntry(GuideTimelineEntry entry) {
        return encodeEntryObject(entry).toString();
    }

    public GuideTimelineEntry decodeEntry(String json) {
        return decodeEntryObject(object(dev.openallay.json.JsonTrees.parse(json), "timeline entry"));
    }

    public List<GuideTimelineEntry> decodeTimeline(String json) {
        JsonElement parsed = dev.openallay.json.JsonTrees.parse(json);
        if (!parsed.isJsonArray()) {
            throw new IllegalArgumentException("durable timeline must be an array");
        }
        List<GuideTimelineEntry> decoded = new ArrayList<>();
        for (JsonElement element : parsed.getAsJsonArray()) {
            decoded.add(decodeEntryObject(object(element, "timeline entry")));
        }
        for (int index = 0; index < decoded.size(); index++) {
            if (decoded.get(index).ordinal() != index) {
                throw new IllegalArgumentException("durable timeline ordinals must be contiguous");
            }
        }
        return List.copyOf(decoded);
    }

    private JsonObject encodeEntryObject(GuideTimelineEntry entry) {
        java.util.Objects.requireNonNull(entry);
        final class $oaPattern0_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.User bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = entry) instanceof dev.openallay.guide.GuideTimelineEntry.User && (($oaPattern0_holder.bound = (GuideTimelineEntry.User) $oaPattern0_holder.value) != null))) {
            return encodeUser($oaPattern0_holder.bound);
        } else {
final class $oaPattern1_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Assistant bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = entry) instanceof dev.openallay.guide.GuideTimelineEntry.Assistant && (($oaPattern1_holder.bound = (GuideTimelineEntry.Assistant) $oaPattern1_holder.value) != null))) {
            return encodeAssistant($oaPattern1_holder.bound);
        } else {
final class $oaPattern2_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Tool bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = entry) instanceof dev.openallay.guide.GuideTimelineEntry.Tool && (($oaPattern2_holder.bound = (GuideTimelineEntry.Tool) $oaPattern2_holder.value) != null))) {
            return encodeTool($oaPattern2_holder.bound);
        }
}
}
        throw new IncompatibleClassChangeError();
    }

    private GuideTimelineEntry decodeEntryObject(JsonObject object) {
        String type = string(object, "type");
        return switch (type) {
            case "user" -> decodeUser(object);
            case "assistant" -> decodeAssistant(object);
            case "tool" -> decodeTool(object);
            default -> throw new IllegalArgumentException(
                    "unknown durable timeline entry type " + type);
        };
    }

    private static JsonObject encodeUser(GuideTimelineEntry.User user) {
        JsonObject object = new JsonObject();
        object.addProperty("type", "user");
        object.addProperty("ordinal", user.ordinal());
        object.addProperty("messageId", user.messageId().toString());
        object.addProperty("text", user.text());
        return object;
    }

    private static GuideTimelineEntry.User decodeUser(JsonObject object) {
        requireFields(object, USER_FIELDS, "user timeline entry");
        return new GuideTimelineEntry.User(integer(object, "ordinal"),
                java.util.UUID.fromString(string(object, "messageId")), string(object, "text"));
    }

    private JsonObject encodeAssistant(GuideTimelineEntry.Assistant assistant) {
        JsonObject object = new JsonObject();
        object.addProperty("type", "assistant");
        object.addProperty("ordinal", assistant.ordinal());
        object.addProperty("text", assistant.text());
        object.add("semantic", semanticDocuments.encodeObject(assistant.semantic()));
        object.addProperty("streaming", assistant.streaming());
        object.add("sources", encodeSourcesArray(assistant.sources()));
        return object;
    }

    private static JsonObject encodeTool(GuideTimelineEntry.Tool tool) {
        GuideToolActivity activity = tool.activity();
        JsonObject object = new JsonObject();
        object.addProperty("type", "tool");
        object.addProperty("ordinal", tool.ordinal());
        object.addProperty("invocationId", activity.invocationId());
        object.addProperty("index", activity.index());
        object.addProperty("toolId", activity.toolId());
        object.addProperty("status", activity.status().name());
        object.add("invocation", encodeInvocation(activity.invocation()));
        object.add("presentationMessages", GuideToolMessageCodec.encode(
                activity.presentationMessages()));
        object.add("sources", encodeSourcesArray(activity.sources()));
        return object;
    }

    private GuideTimelineEntry.Assistant decodeAssistant(JsonObject object) {
        requireFields(object, ASSISTANT_FIELDS, "assistant timeline entry");
        return new GuideTimelineEntry.Assistant(
                integer(object, "ordinal"),
                string(object, "text"),
                semanticDocuments.decodeObject(object(object.get("semantic"), "semantic document")),
                bool(object, "streaming"),
                decodeSourcesArray(array(object, "sources")));
    }

    private static GuideTimelineEntry.Tool decodeTool(JsonObject object) {
        requireFields(object, TOOL_FIELDS, "tool timeline entry");
        GuideToolActivity activity = new GuideToolActivity(
                string(object, "invocationId"),
                integer(object, "index"),
                string(object, "toolId"),
                enumValue(GuideToolStatus.class, string(object, "status"), "tool status"),
                null,
                decodeInvocation(object(object.get("invocation"), "tool invocation")),
                null,
                GuideToolMessageCodec.decode(object.get("presentationMessages")),
                decodeSourcesArray(array(object, "sources")));
        return new GuideTimelineEntry.Tool(integer(object, "ordinal"), activity);
    }

    private static JsonObject encodeInvocation(GuideToolInvocationView invocation) {
        JsonObject object = new JsonObject();
        object.add("handles", encodeStrings(invocation.handles()));
        object.add("modules", encodeStrings(invocation.modules()));
        return object;
    }

    private static GuideToolInvocationView decodeInvocation(JsonObject object) {
        requireFields(object, TOOL_INVOCATION_FIELDS, "tool invocation");
        return new GuideToolInvocationView(
                decodeStrings(array(object, "handles")),
                decodeStrings(array(object, "modules")),
                false);
    }

    private static JsonArray encodeStrings(List<String> values) {
        JsonArray encoded = new JsonArray();
        values.forEach(encoded::add);
        return encoded;
    }

    private static List<String> decodeStrings(JsonArray values) {
        ArrayList<String> decoded = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("tool invocation value must be text");
            }
            decoded.add(value.getAsString());
        }
        return List.copyOf(decoded);
    }

    public String encodeSources(List<GuideSource> sources) {
        return encodeSourcesArray(sources).toString();
    }

    public List<GuideSource> decodeSources(String json) {
        JsonElement parsed = dev.openallay.json.JsonTrees.parse(json);
        if (!parsed.isJsonArray()) {
            throw new IllegalArgumentException("durable sources must be an array");
        }
        return decodeSourcesArray(parsed.getAsJsonArray());
    }

    private static JsonArray encodeSourcesArray(List<GuideSource> sources) {
        JsonArray encoded = new JsonArray();
        for (GuideSource source : sources) {
            JsonObject object = new JsonObject();
            object.addProperty("toolId", source.toolId());
            object.add("evidence", encodeEvidence(source.evidence()));
            object.addProperty("lastCapturedAt", source.lastCapturedAt().toString());
            encoded.add(object);
        }
        return encoded;
    }

    private static List<GuideSource> decodeSourcesArray(JsonArray sources) {
        List<GuideSource> decoded = new ArrayList<>();
        for (JsonElement element : sources) {
            JsonObject object = object(element, "durable source");
            requireFields(object, SOURCE_FIELDS, "durable source");
            decoded.add(new GuideSource(
                    string(object, "toolId"),
                    decodeEvidence(object(object.get("evidence"), "durable evidence")),
                    Instant.parse(string(object, "lastCapturedAt"))));
        }
        return List.copyOf(decoded);
    }

    private static JsonObject encodeEvidence(EvidenceMetadata evidence) {
        JsonObject object = new JsonObject();
        object.addProperty("authority", evidence.authority().name());
        object.addProperty("completeness", evidence.completeness().name());
        object.addProperty("capturedAt", evidence.capturedAt().toString());
        object.addProperty("sourceId", evidence.sourceId());
        object.addProperty("provenance", evidence.provenance());
        object.addProperty("gameVersion", evidence.gameVersion());
        object.addProperty("loader", evidence.loader());
        JsonObject details = new JsonObject();
        evidence.details().forEach(details::addProperty);
        object.add("details", details);
        return object;
    }

    private static EvidenceMetadata decodeEvidence(JsonObject object) {
        requireFields(object, EVIDENCE_FIELDS, "durable evidence");
        JsonObject encodedDetails = object(object.get("details"), "evidence details");
        Map<String, String> details = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : encodedDetails.entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("evidence detail must be text");
            }
            details.put(entry.getKey(), value.getAsString());
        }
        return new EvidenceMetadata(
                enumValue(DataAuthority.class, string(object, "authority"), "authority"),
                enumValue(DataCompleteness.class, string(object, "completeness"), "completeness"),
                Instant.parse(string(object, "capturedAt")),
                string(object, "sourceId"),
                string(object, "provenance"),
                string(object, "gameVersion"),
                string(object, "loader"),
                details);
    }

    private static void requireFields(JsonObject object, Set<String> expected, String label) {
        if (!dev.openallay.json.JsonTrees.keys(object).equals(expected)) {
            Set<String> missing = new java.util.TreeSet<>(expected);
            missing.removeAll(dev.openallay.json.JsonTrees.keys(object));
            Set<String> extra = new java.util.TreeSet<>(dev.openallay.json.JsonTrees.keys(object));
            extra.removeAll(expected);
            throw new IllegalArgumentException(
                    label + " schema mismatch; missing=" + missing + ", extra=" + extra);
        }
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return value.getAsString();
    }

    private static int integer(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException(field + " must be an integer", failure);
        }
    }

    private static boolean bool(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be boolean");
        }
        return value.getAsBoolean();
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("unknown durable " + label + " " + value, failure);
        }
    }
}
