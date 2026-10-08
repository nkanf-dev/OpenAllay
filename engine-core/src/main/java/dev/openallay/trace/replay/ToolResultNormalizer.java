package dev.openallay.trace.replay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.context.EvidenceBearing;
import dev.openallay.json.EngineJson;
import dev.openallay.tool.ModelFacingToolOutput;
import dev.openallay.tool.ToolResult;
import java.util.Objects;
import java.util.TreeSet;

public final class ToolResultNormalizer {
    private final Gson gson;

    public ToolResultNormalizer(Gson gson) {
        this.gson = EngineJson.withInstant(Objects.requireNonNull(gson, "gson"));
    }

    public JsonObject normalize(ToolResult<?> result, Class<?> outputType) {
        JsonObject normalized = new JsonObject();
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<?> value; ToolResult.Success<?> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<?>) $oaPattern0_holder.value) != null))) {
            if (EvidenceBearing.class.isAssignableFrom(outputType)) {
                final class $oaPattern1_Holder { java.lang.Object value; EvidenceBearing bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if (!((($oaPattern1_holder.value = $oaPattern0_holder.bound.value()) instanceof dev.openallay.context.EvidenceBearing && (($oaPattern1_holder.bound = (EvidenceBearing) $oaPattern1_holder.value) != null)))
                        || $oaPattern1_holder.bound.evidence() == null
                        || $oaPattern1_holder.bound.evidence().isEmpty()) {
                    throw new IllegalArgumentException("Grounded tool output has no evidence");
                }
            }
            normalized.addProperty("status", "success");
            normalized.addProperty("outputType", outputType.getName());
            normalized.add("value", canonicalize(gson.toJsonTree($oaPattern0_holder.bound.value())));
            final class $oaPattern2_Holder { java.lang.Object value; ModelFacingToolOutput bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = $oaPattern0_holder.bound.value()) instanceof dev.openallay.tool.ModelFacingToolOutput && (($oaPattern2_holder.bound = (ModelFacingToolOutput) $oaPattern2_holder.value) != null))) {
                String text = $oaPattern2_holder.bound.modelText();
                if (text == null || dev.openallay.util.Java8Strings.isBlank(text)) {
                    throw new IllegalArgumentException("Model-facing tool text must not be blank");
                }
                normalized.addProperty("modelText", text);
            }
        } else {
final class $oaPattern3_Holder { dev.openallay.tool.ToolResult<?> value; ToolResult.Failure<?> bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = result) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern3_holder.bound = (ToolResult.Failure<?>) $oaPattern3_holder.value) != null))) {
            normalized.addProperty("status", "failure");
            normalized.addProperty("code", $oaPattern3_holder.bound.code());
            normalized.addProperty("message", $oaPattern3_holder.bound.message());
        } else {
            throw new IllegalArgumentException("Unknown ToolResult implementation: " + result);
        }
}
        return canonicalize(normalized).getAsJsonObject();
    }

    public JsonElement canonicalize(JsonElement value) {
        if (value == null || value.isJsonNull() || value.isJsonPrimitive()) {
            return value == null ? null : dev.openallay.json.JsonTrees.copy(value);
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement element : value.getAsJsonArray()) {
                result.add(canonicalize(element));
            }
            return result;
        }

        JsonObject result = new JsonObject();
        JsonObject object = value.getAsJsonObject();
        for (String key : new TreeSet<>(dev.openallay.json.JsonTrees.keys(object))) {
            result.add(key, canonicalize(object.get(key)));
        }
        return result;
    }
}
