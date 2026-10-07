package dev.openallay.trace.model;

import dev.openallay.context.ContextCapability;
import java.util.List;
import java.util.Set;

@dev.openallay.value.ValueType(AgentTrace.ValueSchemaProvider.class)
public final class AgentTrace {
    private final String id;
    private final String userMessage;
    private final Set<ContextCapability> requiredContext;
    private final List<TraceStep> steps;
    public AgentTrace(String id, String userMessage, Set<ContextCapability> requiredContext, List<TraceStep> steps) {

        if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]*")) {
            throw new IllegalArgumentException("Invalid trace id: " + id);
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        requiredContext = Set.copyOf(requiredContext);
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Trace must contain at least one step");
        }

        this.id = id;
        this.userMessage = userMessage;
        this.requiredContext = requiredContext;
        this.steps = steps;
    }
    public String id() { return id; }
    public String userMessage() { return userMessage; }
    public Set<ContextCapability> requiredContext() { return requiredContext; }
    public List<TraceStep> steps() { return steps; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AgentTrace)) return false;
        AgentTrace that = (AgentTrace) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(userMessage, that.userMessage) && java.util.Objects.equals(requiredContext, that.requiredContext) && java.util.Objects.equals(steps, that.steps);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(userMessage);
        hash = 31 * hash + java.util.Objects.hashCode(requiredContext);
        hash = 31 * hash + java.util.Objects.hashCode(steps);
        return hash;
    }
    @Override public String toString() { return "AgentTrace[id=" + id + ", userMessage=" + userMessage + ", requiredContext=" + requiredContext + ", steps=" + steps + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<AgentTrace> schema() {
            return new dev.openallay.value.ValueSchema<>(AgentTrace.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<AgentTrace>>asList(new dev.openallay.value.ValueSchema.Component<>(AgentTrace.class, "id", AgentTrace::id), new dev.openallay.value.ValueSchema.Component<>(AgentTrace.class, "userMessage", AgentTrace::userMessage), new dev.openallay.value.ValueSchema.Component<>(AgentTrace.class, "requiredContext", AgentTrace::requiredContext), new dev.openallay.value.ValueSchema.Component<>(AgentTrace.class, "steps", AgentTrace::steps)), arguments -> new AgentTrace((String) arguments[0], (String) arguments[1], (Set) arguments[2], (List) arguments[3]));
        }
    }
}
