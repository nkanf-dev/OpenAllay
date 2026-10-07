package dev.openallay.guide;

import dev.openallay.guide.semantic.SemanticDocument;
import dev.openallay.guide.semantic.SemanticMessageParser;
import java.util.List;

public sealed interface GuideTimelineEntry
        permits GuideTimelineEntry.User, GuideTimelineEntry.Assistant, GuideTimelineEntry.Tool {
    int ordinal();

    /** An instruction actually admitted during this request, not a queued draft. */
    @dev.openallay.value.ValueType(User.ValueSchemaProvider.class)
public static final class User implements GuideTimelineEntry {
    private final int ordinal;
    private final java.util.UUID messageId;
    private final String text;
    public User(int ordinal, java.util.UUID messageId, String text) {

            requireOrdinal(ordinal);
            java.util.Objects.requireNonNull(messageId, "messageId");
            java.util.Objects.requireNonNull(text, "text");

        this.ordinal = ordinal;
        this.messageId = messageId;
        this.text = text;
    }
    public int ordinal() { return ordinal; }
    public java.util.UUID messageId() { return messageId; }
    public String text() { return text; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof User)) return false;
        User that = (User) other;
        return ordinal == that.ordinal && java.util.Objects.equals(messageId, that.messageId) && java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "User[ordinal=" + ordinal + ", messageId=" + messageId + ", text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<User> schema() {
            return new dev.openallay.value.ValueSchema<>(User.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<User>>asList(new dev.openallay.value.ValueSchema.Component<>(User.class, "ordinal", User::ordinal), new dev.openallay.value.ValueSchema.Component<>(User.class, "messageId", User::messageId), new dev.openallay.value.ValueSchema.Component<>(User.class, "text", User::text)), arguments -> new User((Integer) arguments[0], (java.util.UUID) arguments[1], (String) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(Assistant.ValueSchemaProvider.class)
public static final class Assistant implements GuideTimelineEntry {
    private final int ordinal;
    private final String text;
    private final SemanticDocument semantic;
    private final boolean streaming;
    private final List<GuideSource> sources;
    public Assistant(int ordinal, String text, SemanticDocument semantic, boolean streaming, List<GuideSource> sources) {

            requireOrdinal(ordinal);
            text = text == null ? "" : text;
            java.util.Objects.requireNonNull(semantic, "semantic");
            sources = dev.openallay.util.Java8Collections.listCopyOf(sources);

        this.ordinal = ordinal;
        this.text = text;
        this.semantic = semantic;
        this.streaming = streaming;
        this.sources = sources;
    }
    public int ordinal() { return ordinal; }
    public String text() { return text; }
    public SemanticDocument semantic() { return semantic; }
    public boolean streaming() { return streaming; }
    public List<GuideSource> sources() { return sources; }
private static final SemanticMessageParser DEFAULT_PARSER = new SemanticMessageParser();
public Assistant(
                int ordinal,
                String text,
                boolean streaming,
                List<GuideSource> sources) {
            this(
                    ordinal,
                    text,
                    DEFAULT_PARSER.parse(text),
                    streaming,
                    sources);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Assistant)) return false;
        Assistant that = (Assistant) other;
        return ordinal == that.ordinal && java.util.Objects.equals(text, that.text) && java.util.Objects.equals(semantic, that.semantic) && streaming == that.streaming && java.util.Objects.equals(sources, that.sources);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(semantic);
        hash = 31 * hash + Boolean.hashCode(streaming);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        return hash;
    }
    @Override public String toString() { return "Assistant[ordinal=" + ordinal + ", text=" + text + ", semantic=" + semantic + ", streaming=" + streaming + ", sources=" + sources + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Assistant> schema() {
            return new dev.openallay.value.ValueSchema<>(Assistant.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Assistant>>asList(new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "ordinal", Assistant::ordinal), new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "text", Assistant::text), new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "semantic", Assistant::semantic), new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "streaming", Assistant::streaming), new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "sources", Assistant::sources)), arguments -> new Assistant((Integer) arguments[0], (String) arguments[1], (SemanticDocument) arguments[2], (Boolean) arguments[3], (List) arguments[4]));
        }
    }
}

    @dev.openallay.value.ValueType(Tool.ValueSchemaProvider.class)
public static final class Tool implements GuideTimelineEntry {
    private final int ordinal;
    private final GuideToolActivity activity;
    public Tool(int ordinal, GuideToolActivity activity) {

            requireOrdinal(ordinal);
            java.util.Objects.requireNonNull(activity, "activity");

        this.ordinal = ordinal;
        this.activity = activity;
    }
    public int ordinal() { return ordinal; }
    public GuideToolActivity activity() { return activity; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Tool)) return false;
        Tool that = (Tool) other;
        return ordinal == that.ordinal && java.util.Objects.equals(activity, that.activity);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(activity);
        return hash;
    }
    @Override public String toString() { return "Tool[ordinal=" + ordinal + ", activity=" + activity + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Tool> schema() {
            return new dev.openallay.value.ValueSchema<>(Tool.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Tool>>asList(new dev.openallay.value.ValueSchema.Component<>(Tool.class, "ordinal", Tool::ordinal), new dev.openallay.value.ValueSchema.Component<>(Tool.class, "activity", Tool::activity)), arguments -> new Tool((Integer) arguments[0], (GuideToolActivity) arguments[1]));
        }
    }
}

    private static void requireOrdinal(int ordinal) {
        if (ordinal < 0) {
            throw new IllegalArgumentException("timeline ordinal must not be negative");
        }
    }
}
