package dev.openallay.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/** Model-written display intent, never execution state, factual output or evidence. */
public record GuideToolIntent(String title, String description) {
    public GuideToolIntent {
        title = plainText(title);
        description = plainText(description);
    }

    public static GuideToolIntent none() {
        return new GuideToolIntent("", "");
    }

    public boolean empty() {
        return title.isEmpty() && description.isEmpty();
    }

    public static GuideToolIntent from(
            String toolId, JsonObject arguments, List<GuideToolMessage> messages) {
        if (toolId == null || !toolId.endsWith(":run_javascript")) return none();
        if (arguments != null && (arguments.has("title") || arguments.has("description"))) {
            return fromArguments(arguments);
        }
        for (GuideToolMessage message : messages) {
            if (message.key() == GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT) {
                return message.arguments().size() == 2
                        ? new GuideToolIntent(message.arguments().get(0), message.arguments().get(1))
                        : none();
            }
        }
        return none();
    }

    public static GuideToolIntent fromArguments(JsonObject arguments) {
        return new GuideToolIntent(string(arguments, "title"), string(arguments, "description"));
    }

    private static String string(JsonObject arguments, String field) {
        JsonElement value = arguments == null ? null : arguments.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static String plainText(String value) {
        if (value == null) return "";
        StringBuilder text = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) text.append(' ');
            else text.appendCodePoint(codePoint);
        });
        return text.toString().strip();
    }
}
