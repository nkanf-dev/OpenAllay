package dev.openallay.command;

/** The five argument positions in the guide and development command grammars. */
public enum CommandArgument {
    NONE("", Kind.NONE),
    ID_WORD("id", Kind.WORD),
    QUESTION_GREEDY("question", Kind.GREEDY),
    TRACE_WORD("trace", Kind.WORD),
    TOOL_GREEDY("tool", Kind.GREEDY);

    public enum Kind { NONE, WORD, GREEDY }

    private final String argumentName;
    private final Kind kind;

    CommandArgument(String argumentName, Kind kind) {
        this.argumentName = argumentName;
        this.kind = kind;
    }

    public String argumentName() { return argumentName; }
    public Kind kind() { return kind; }
}
