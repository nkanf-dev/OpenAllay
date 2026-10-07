package dev.openallay.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/** Model-written display intent, never execution state, factual output or evidence. */
@dev.openallay.value.ValueType(GuideToolIntent.ValueSchemaProvider.class)
public final class GuideToolIntent {
    private final String title;
    private final String description;
    public GuideToolIntent(String title, String description) {

        title = plainText(title);
        description = plainText(description);

        this.title = title;
        this.description = description;
    }
    public String title() { return title; }
    public String description() { return description; }
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideToolIntent)) return false;
        GuideToolIntent that = (GuideToolIntent) other;
        return java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        return hash;
    }
    @Override public String toString() { return "GuideToolIntent[title=" + title + ", description=" + description + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideToolIntent> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideToolIntent.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideToolIntent>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideToolIntent.class, "title", GuideToolIntent::title), new dev.openallay.value.ValueSchema.Component<>(GuideToolIntent.class, "description", GuideToolIntent::description)), arguments -> new GuideToolIntent((String) arguments[0], (String) arguments[1]));
        }
    }
}
