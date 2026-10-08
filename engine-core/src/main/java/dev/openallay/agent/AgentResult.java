package dev.openallay.agent;

import dev.openallay.agent.trace.LiveAgentTrace;

@dev.openallay.value.ValueType(AgentResult.ValueSchemaProvider.class)
public final class AgentResult {
    private final AgentState state;
    private final String text;
    private final String errorCode;
    private final String errorMessage;
    private final LiveAgentTrace trace;
    public AgentResult(AgentState state, String text, String errorCode, String errorMessage, LiveAgentTrace trace) {
        this.state = state;
        this.text = text;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.trace = trace;
    }
    public AgentState state() { return state; }
    public String text() { return text; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public LiveAgentTrace trace() { return trace; }
public boolean successful() {
        return state == AgentState.COMPLETED;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AgentResult)) return false;
        AgentResult that = (AgentResult) other;
        return java.util.Objects.equals(state, that.state) && java.util.Objects.equals(text, that.text) && java.util.Objects.equals(errorCode, that.errorCode) && java.util.Objects.equals(errorMessage, that.errorMessage) && java.util.Objects.equals(trace, that.trace);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(errorCode);
        hash = 31 * hash + java.util.Objects.hashCode(errorMessage);
        hash = 31 * hash + java.util.Objects.hashCode(trace);
        return hash;
    }
    @Override public String toString() { return "AgentResult[state=" + state + ", text=" + text + ", errorCode=" + errorCode + ", errorMessage=" + errorMessage + ", trace=" + trace + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<AgentResult> schema() {
            return new dev.openallay.value.ValueSchema<>(AgentResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<AgentResult>>asList(new dev.openallay.value.ValueSchema.Component<>(AgentResult.class, "state", AgentResult::state), new dev.openallay.value.ValueSchema.Component<>(AgentResult.class, "text", AgentResult::text), new dev.openallay.value.ValueSchema.Component<>(AgentResult.class, "errorCode", AgentResult::errorCode), new dev.openallay.value.ValueSchema.Component<>(AgentResult.class, "errorMessage", AgentResult::errorMessage), new dev.openallay.value.ValueSchema.Component<>(AgentResult.class, "trace", AgentResult::trace)), arguments -> new AgentResult((AgentState) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (LiveAgentTrace) arguments[4]));
        }
    }
}
