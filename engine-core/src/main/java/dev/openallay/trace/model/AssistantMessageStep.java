package dev.openallay.trace.model;

@dev.openallay.value.ValueType(AssistantMessageStep.ValueSchemaProvider.class)
public final class AssistantMessageStep implements TraceStep {
    private final String content;
    public AssistantMessageStep(String content) {

        if (content == null || dev.openallay.util.Java8Strings.isBlank(content)) {
            throw new IllegalArgumentException("Assistant message content must not be blank");
        }

        this.content = content;
    }
    public String content() { return content; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AssistantMessageStep)) return false;
        AssistantMessageStep that = (AssistantMessageStep) other;
        return java.util.Objects.equals(content, that.content);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(content);
        return hash;
    }
    @Override public String toString() { return "AssistantMessageStep[content=" + content + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<AssistantMessageStep> schema() {
            return new dev.openallay.value.ValueSchema<>(AssistantMessageStep.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<AssistantMessageStep>>asList(new dev.openallay.value.ValueSchema.Component<>(AssistantMessageStep.class, "content", AssistantMessageStep::content)), arguments -> new AssistantMessageStep((String) arguments[0]));
        }
    }
}
