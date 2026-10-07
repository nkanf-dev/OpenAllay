package dev.openallay.guide;

import java.util.List;
import java.util.Objects;

/** Closed locale-independent Tool presentation message safe for history and bridge transport. */
@dev.openallay.value.ValueType(GuideToolMessage.ValueSchemaProvider.class)
public final class GuideToolMessage {
    private final Key key;
    private final List<String> arguments;
    public GuideToolMessage(Key key, List<String> arguments) {

        Objects.requireNonNull(key, "key");
        arguments = dev.openallay.util.Java8Collections.listCopyOf(arguments);
        for (String argument : arguments) requireSafeArgument(argument);

        this.key = key;
        this.arguments = arguments;
    }
    public Key key() { return key; }
    public List<String> arguments() { return arguments; }
public enum Key {
        INVOCATION_LOAD_SKILL("screen.openallay.tool.message.invocation.load_skill"),
        INVOCATION_LOAD_SKILL_EXACT("screen.openallay.tool.message.invocation.load_skill_exact"),
        INVOCATION_LOAD_SKILL_REFERENCE("screen.openallay.tool.message.invocation.load_skill_reference"),
        INVOCATION_RUN_JAVASCRIPT("screen.openallay.tool.message.invocation.run_javascript"),

        RESULT_PENDING("screen.openallay.tool.message.result.pending"),
        RESULT_DETAIL_NOT_STORED("screen.openallay.tool.message.result.detail_not_stored"),
        RESULT_VALUE_UNAVAILABLE("screen.openallay.tool.message.result.value_unavailable"),
        RESULT_COMPLETED("screen.openallay.tool.message.result.completed"),
        ANALYSIS_EMPTY("screen.openallay.tool.message.analysis.empty"),
        ANALYSIS_COMPLETE("screen.openallay.tool.message.analysis.complete"),
        ANALYSIS_PREVIEW("screen.openallay.tool.message.analysis.preview"),
        ANALYSIS_FIELDS_COMPLETE("screen.openallay.tool.message.analysis.fields_complete"),
        ANALYSIS_FIELDS_PREVIEW("screen.openallay.tool.message.analysis.fields_preview"),
        ANALYSIS_VALUE_COMPLETE("screen.openallay.tool.message.analysis.value_complete"),
        ANALYSIS_VALUE_PREVIEW("screen.openallay.tool.message.analysis.value_preview"),
        ANALYSIS_WORKSPACE("screen.openallay.tool.message.analysis.workspace"),
        FAILURE_STALE_REFERENCE("screen.openallay.tool.message.failure.stale_reference"),
        FAILURE_UNAVAILABLE("screen.openallay.tool.message.failure.unavailable"),
        FAILURE_PLAYER_REQUIRED("screen.openallay.tool.message.failure.player_required"),
        FAILURE_INVALID_ARGUMENTS("screen.openallay.tool.message.failure.invalid_arguments"),
        FAILURE_FORBIDDEN("screen.openallay.tool.message.failure.forbidden"),
        FAILURE_GENERIC("screen.openallay.tool.message.failure.generic"),
        SKILL_LOADED("screen.openallay.tool.message.skill.loaded"),
        SKILL_TOOLS("screen.openallay.tool.message.skill.tools");

        private final String translationKey;

        Key(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }
public static GuideToolMessage of(Key key, String... arguments) {
        return new GuideToolMessage(key, dev.openallay.util.Java8Collections.listOf(arguments));
    }
private static void requireSafeArgument(String argument) {
        Objects.requireNonNull(argument, "Tool message argument");
        if (argument.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Tool message arguments must not contain control characters");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideToolMessage)) return false;
        GuideToolMessage that = (GuideToolMessage) other;
        return java.util.Objects.equals(key, that.key) && java.util.Objects.equals(arguments, that.arguments);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + java.util.Objects.hashCode(arguments);
        return hash;
    }
    @Override public String toString() { return "GuideToolMessage[key=" + key + ", arguments=" + arguments + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideToolMessage> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideToolMessage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideToolMessage>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideToolMessage.class, "key", GuideToolMessage::key), new dev.openallay.value.ValueSchema.Component<>(GuideToolMessage.class, "arguments", GuideToolMessage::arguments)), arguments -> new GuideToolMessage((Key) arguments[0], (List) arguments[1]));
        }
    }
}
