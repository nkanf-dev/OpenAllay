package dev.openallay.guide.composer;

import java.util.List;

/** Local composer syntax. This registry contains only implemented OpenAllay commands. */
public final class SlashCommandParser {
    private SlashCommandParser() {}

    public enum Kind { TEXT, COMMAND, ERROR }
    public record Parsed(Kind kind, String text, String code) {}
    public record Suggestion(String command, String descriptionKey) {}

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
