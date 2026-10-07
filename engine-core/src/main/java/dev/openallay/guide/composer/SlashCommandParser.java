package dev.openallay.guide.composer;

import java.util.List;

/** Local composer syntax. This registry contains only implemented OpenAllay commands. */
public final class SlashCommandParser {
    private SlashCommandParser() {}

    public enum Kind { TEXT, COMMAND, ERROR }
    @dev.openallay.value.ValueType(Parsed.ValueSchemaProvider.class)
public static final class Parsed {
    private final Kind kind;
    private final String text;
    private final String code;
    public Parsed(Kind kind, String text, String code) {
        this.kind = kind;
        this.text = text;
        this.code = code;
    }
    public Kind kind() { return kind; }
    public String text() { return text; }
    public String code() { return code; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Parsed)) return false;
        Parsed that = (Parsed) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(text, that.text) && java.util.Objects.equals(code, that.code);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        return hash;
    }
    @Override public String toString() { return "Parsed[kind=" + kind + ", text=" + text + ", code=" + code + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Parsed> schema() {
            return new dev.openallay.value.ValueSchema<>(Parsed.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Parsed>>asList(new dev.openallay.value.ValueSchema.Component<>(Parsed.class, "kind", Parsed::kind), new dev.openallay.value.ValueSchema.Component<>(Parsed.class, "text", Parsed::text), new dev.openallay.value.ValueSchema.Component<>(Parsed.class, "code", Parsed::code)), arguments -> new Parsed((Kind) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(Suggestion.ValueSchemaProvider.class)
public static final class Suggestion {
    private final String command;
    private final String descriptionKey;
    public Suggestion(String command, String descriptionKey) {
        this.command = command;
        this.descriptionKey = descriptionKey;
    }
    public String command() { return command; }
    public String descriptionKey() { return descriptionKey; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Suggestion)) return false;
        Suggestion that = (Suggestion) other;
        return java.util.Objects.equals(command, that.command) && java.util.Objects.equals(descriptionKey, that.descriptionKey);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(command);
        hash = 31 * hash + java.util.Objects.hashCode(descriptionKey);
        return hash;
    }
    @Override public String toString() { return "Suggestion[command=" + command + ", descriptionKey=" + descriptionKey + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Suggestion> schema() {
            return new dev.openallay.value.ValueSchema<>(Suggestion.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Suggestion>>asList(new dev.openallay.value.ValueSchema.Component<>(Suggestion.class, "command", Suggestion::command), new dev.openallay.value.ValueSchema.Component<>(Suggestion.class, "descriptionKey", Suggestion::descriptionKey)), arguments -> new Suggestion((String) arguments[0], (String) arguments[1]));
        }
    }
}

    public static Parsed parse(String draft) {
        String text = draft == null ? "" : draft.strip();
        if (!text.startsWith("/")) return new Parsed(Kind.TEXT, text, null);
        if (text.startsWith("//")) return new Parsed(Kind.TEXT, text.substring(1), null);
        int separator = 1;
        while (separator < text.length() && !Character.isWhitespace(text.charAt(separator))) separator++;
        String command = text.substring(1, separator);
        if (!"compact".equals(command)) return new Parsed(Kind.ERROR, text, "unknown_slash_command");
        if (!text.substring(separator).isBlank()) return new Parsed(Kind.ERROR, text, "invalid_slash_arguments");
        return new Parsed(Kind.COMMAND, command, null);
    }

    public static List<Suggestion> suggestions(String draft) {
        String text = draft == null ? "" : draft.stripLeading();
        if (!text.startsWith("/") || text.startsWith("//") || text.chars().anyMatch(Character::isWhitespace)) {
            return List.of();
        }
        return "/compact".startsWith(text)
                ? List.of(new Suggestion("/compact", "openallay.guide.slash.compact.help")) : List.of();
    }
}
