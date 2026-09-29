package dev.openallay.guide;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.regex.Pattern;

/** Closed, non-sensitive player projection of a Tool invocation. */
public final class GuideToolInvocationPresentation {
    private static final Pattern SAFE_NAME = Pattern.compile("[a-zA-Z0-9_.-]+");

    private GuideToolInvocationPresentation() {}

    public static List<GuideToolMessage> messages(String toolId, JsonObject input) {
        String name = toolName(toolId);
        return switch (name) {
            case "load_skill" -> loadSkill(input);
            case "run_javascript" -> one(GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT);
            default -> List.of();
        };
    }

    private static List<GuideToolMessage> loadSkill(JsonObject input) {
        String name = safeName(input, "name");
        String reference = safeReference(primitive(input, "reference"));
        if (!reference.isEmpty()) {
            return List.of(GuideToolMessage.of(
                    GuideToolMessage.Key.INVOCATION_LOAD_SKILL_REFERENCE,
                    name,
                    reference));
        }
        return optional(
                GuideToolMessage.Key.INVOCATION_LOAD_SKILL,
                GuideToolMessage.Key.INVOCATION_LOAD_SKILL_EXACT,
                name);
    }

    private static String safeName(JsonObject input, String field) {
        String value = primitive(input, field);
        return SAFE_NAME.matcher(value).matches() ? value : "";
    }

    private static String safeReference(String value) {
        return value.matches("references/[a-zA-Z0-9][a-zA-Z0-9._/-]*\\.md")
                        && !value.contains("/../")
                        && !value.contains("//")
                ? value
                : "";
    }

    private static String primitive(JsonObject input, String field) {
        return input != null && input.has(field) && input.get(field).isJsonPrimitive()
                ? input.get(field).getAsString()
                : "";
    }

    private static String toolName(String toolId) {
        int separator = toolId.indexOf(':');
        return separator < 0 ? toolId : toolId.substring(separator + 1);
    }

    private static List<GuideToolMessage> one(GuideToolMessage.Key key) {
        return List.of(GuideToolMessage.of(key));
    }

    private static List<GuideToolMessage> optional(
            GuideToolMessage.Key plain,
            GuideToolMessage.Key exact,
            String value) {
        return List.of(value.isBlank()
                ? GuideToolMessage.of(plain)
                : GuideToolMessage.of(exact, value));
    }
}
