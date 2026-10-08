package dev.openallay.trace.model;

public interface TraceStep {
    /** Exact Java8 replacement for the original closed two-step union. */
    static void requireKnown(TraceStep step) {
        java.util.Objects.requireNonNull(step, "step");
        Class<?> type = step.getClass();
        if (type != ToolCallStep.class && type != AssistantMessageStep.class) {
            throw new IncompatibleClassChangeError("Unknown trace step subtype");
        }
    }
}
