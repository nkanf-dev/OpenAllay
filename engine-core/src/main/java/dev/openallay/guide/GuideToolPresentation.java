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
        {
java.util.List<dev.openallay.guide.GuideToolMessage> $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((toolName(toolId))) {
case "load_skill":
{
$oaSwitch0_exit_result = loadedSkill(value); break $oaSwitch0_exit;
}
case "run_javascript":
{
$oaSwitch0_exit_result = javascriptAnalysis(value); break $oaSwitch0_exit;
}
default:
{
$oaSwitch0_exit_result = one(GuideToolMessage.Key.RESULT_COMPLETED); break $oaSwitch0_exit;
}
}
}
return $oaSwitch0_exit_result;
}
    }

    private static List<GuideToolMessage> javascriptAnalysis(JsonObject value) {
        JsonElement preview = value.get("preview");
        String type = string(value, "resultType");
        long cardinality = value.has("cardinality") && value.get("cardinality").isJsonPrimitive()
                ? Math.max(0, value.get("cardinality").getAsLong())
                : 0;
        boolean complete = value.has("complete")
                && value.get("complete").isJsonPrimitive()
                && value.get("complete").getAsBoolean();
        // Completeness describes the preview, never whether the calculation succeeded.
        // Object cardinality counts top-level fields, not nested rows or result records.
        dev.openallay.guide.GuideToolMessage $oaSwitch2_exit_result;
$oaSwitch2_exit: {
switch ((type)) {
case "array":
{
$oaSwitch2_exit_result = cardinality == 0
                    ? message(GuideToolMessage.Key.ANALYSIS_EMPTY)
                    : complete
                            ? message(GuideToolMessage.Key.ANALYSIS_COMPLETE, Long.toString(cardinality))
                            : message(GuideToolMessage.Key.ANALYSIS_PREVIEW,
                                    Integer.toString(preview != null && preview.isJsonArray()
                                            ? preview.getAsJsonArray().size() : 0),
                                    Long.toString(cardinality)); break $oaSwitch2_exit;
}
case "object":
{
$oaSwitch2_exit_result = complete
                    ? message(GuideToolMessage.Key.ANALYSIS_FIELDS_COMPLETE, Long.toString(cardinality))
                    : message(GuideToolMessage.Key.ANALYSIS_FIELDS_PREVIEW,
                            Integer.toString(preview != null && preview.isJsonObject()
                                    ? preview.getAsJsonObject().size() : 0),
                            Long.toString(cardinality)); break $oaSwitch2_exit;
}
default:
{
$oaSwitch2_exit_result = message(complete ? GuideToolMessage.Key.ANALYSIS_VALUE_COMPLETE
                    : GuideToolMessage.Key.ANALYSIS_VALUE_PREVIEW); break $oaSwitch2_exit;
}
}
}
GuideToolMessage summary = $oaSwitch2_exit_result;
        // This historical receipt does not imply that an old request's handle is still live.
        return !complete && !string(value, "handle").isBlank()
                ? List.of(summary, message(GuideToolMessage.Key.ANALYSIS_WORKSPACE))
                : List.of(summary);
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
        {
dev.openallay.guide.GuideToolMessage.Key $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((code)) {
case "stale_reference":
{
$oaSwitch1_exit_result = GuideToolMessage.Key.FAILURE_STALE_REFERENCE; break $oaSwitch1_exit;
}
case "capability_unavailable":
case "tool_unavailable":
{
$oaSwitch1_exit_result = GuideToolMessage.Key.FAILURE_UNAVAILABLE; break $oaSwitch1_exit;
}
case "player_required":
{
$oaSwitch1_exit_result = GuideToolMessage.Key.FAILURE_PLAYER_REQUIRED; break $oaSwitch1_exit;
}
case "invalid_arguments":
case "invalid_tool_arguments":
{
$oaSwitch1_exit_result = GuideToolMessage.Key.FAILURE_INVALID_ARGUMENTS; break $oaSwitch1_exit;
}
case "unauthorized":
case "forbidden":
{
$oaSwitch1_exit_result = GuideToolMessage.Key.FAILURE_FORBIDDEN; break $oaSwitch1_exit;
}
default:
{
$oaSwitch1_exit_result = GuideToolMessage.Key.FAILURE_GENERIC; break $oaSwitch1_exit;
}
}
}
return $oaSwitch1_exit_result;
}
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
