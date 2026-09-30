package dev.openallay.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/** Deterministic locale-independent player projection of a normalized Tool result. */
public final class GuideToolPresentation {
    private GuideToolPresentation() {}

    public static List<GuideToolMessage> messages(
            String toolId, JsonObject normalized, GuideToolStatus status) {
        if (normalized == null && status == GuideToolStatus.RUNNING) {
            return one(GuideToolMessage.Key.RESULT_PENDING);
        }
        return messages(toolId, normalized);
    }

    /** Projects completed or restored results, which may lack persisted detail. */
    public static List<GuideToolMessage> messages(String toolId, JsonObject normalized) {
        if (normalized == null) {
            return one(GuideToolMessage.Key.RESULT_DETAIL_NOT_STORED);
        }
        if (!"success".equals(string(normalized, "status"))) {
            return one(friendlyFailure(string(normalized, "code")));
        }
        JsonObject value = object(normalized, "value");
        if (value == null) return one(GuideToolMessage.Key.RESULT_VALUE_UNAVAILABLE);
        return switch (toolName(toolId)) {
            case "load_skill" -> loadedSkill(value);
            case "run_javascript" -> javascriptAnalysis(value);
            default -> one(GuideToolMessage.Key.RESULT_COMPLETED);
        };
    }

    private static List<GuideToolMessage> javascriptAnalysis(JsonObject value) {
        long cardinality = value.has("cardinality") && value.get("cardinality").isJsonPrimitive()
                ? Math.max(0, value.get("cardinality").getAsLong())
                : 0;
        if (cardinality == 0) {
            return one(GuideToolMessage.Key.ANALYSIS_EMPTY);
        }
        boolean complete = value.has("complete")
                && value.get("complete").isJsonPrimitive()
                && value.get("complete").getAsBoolean();
        if (complete) {
            return List.of(message(
                    GuideToolMessage.Key.ANALYSIS_COMPLETE, Long.toString(cardinality)));
        }
        JsonElement preview = value.get("preview");
        long shown = preview != null && preview.isJsonArray()
                ? preview.getAsJsonArray().size()
                : 1;
        return List.of(message(
                GuideToolMessage.Key.ANALYSIS_PREVIEW,
                Long.toString(shown),
                Long.toString(cardinality)));
    }

    private static List<GuideToolMessage> loadedSkill(JsonObject value) {
        JsonArray allowed = array(value, "allowedTools");
        return List.of(
                message(GuideToolMessage.Key.SKILL_LOADED, string(value, "name")),
                message(
                        GuideToolMessage.Key.SKILL_TOOLS,
                        Integer.toString(allowed.size()),
                        string(value, "provenance")));
    }

    private static GuideToolMessage.Key friendlyFailure(String code) {
        return switch (code) {
            case "stale_reference" -> GuideToolMessage.Key.FAILURE_STALE_REFERENCE;
            case "capability_unavailable", "tool_unavailable" ->
                    GuideToolMessage.Key.FAILURE_UNAVAILABLE;
            case "player_required" -> GuideToolMessage.Key.FAILURE_PLAYER_REQUIRED;
            case "invalid_arguments", "invalid_tool_arguments" -> GuideToolMessage.Key.FAILURE_INVALID_ARGUMENTS;
            case "unauthorized", "forbidden" -> GuideToolMessage.Key.FAILURE_FORBIDDEN;
            default -> GuideToolMessage.Key.FAILURE_GENERIC;
        };
    }

    private static String toolName(String toolId) {
        int separator = toolId.indexOf(':');
        return separator < 0 ? toolId : toolId.substring(separator + 1);
    }

    private static JsonObject object(JsonObject value, String field) {
        return value != null && value.has(field) && value.get(field).isJsonObject()
                ? value.getAsJsonObject(field) : null;
    }

    private static JsonArray array(JsonObject value, String field) {
        return value != null && value.has(field) && value.get(field).isJsonArray()
                ? value.getAsJsonArray(field) : new JsonArray();
    }

    private static String string(JsonObject value, String field) {
        return value != null && value.has(field) && !value.get(field).isJsonNull()
                ? value.get(field).getAsString() : "";
    }

    private static List<GuideToolMessage> one(GuideToolMessage.Key key) {
        return List.of(message(key));
    }

    private static GuideToolMessage message(
            GuideToolMessage.Key key, String... arguments) {
        String[] safe = new String[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            safe[index] = safe(arguments[index]);
        }
        return GuideToolMessage.of(key, safe);
    }

    private static String safe(String value) {
        StringBuilder safe = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) safe.append(' ');
            else safe.appendCodePoint(codePoint);
        });
        return safe.toString().strip();
    }
}
