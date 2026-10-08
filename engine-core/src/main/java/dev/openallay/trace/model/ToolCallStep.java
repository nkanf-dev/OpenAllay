package dev.openallay.trace.model;

import com.google.gson.JsonObject;
import java.util.Objects;

@dev.openallay.value.ValueType(ToolCallStep.ValueSchemaProvider.class)
public final class ToolCallStep implements TraceStep {
    private final String tool;
    private final JsonObject arguments;
    private final TraceExpectation expect;
    public ToolCallStep(String tool, JsonObject arguments, TraceExpectation expect) {

        if (tool == null || !tool.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid tool id: " + tool);
        }
        arguments = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(expect, "expect");

        this.tool = tool;
        this.arguments = arguments;
        this.expect = expect;
    }
    public String tool() { return tool; }
    public TraceExpectation expect() { return expect; }

    public JsonObject arguments() {
        return dev.openallay.json.JsonTrees.copy(arguments);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolCallStep)) return false;
        ToolCallStep that = (ToolCallStep) other;
        return java.util.Objects.equals(tool, that.tool) && java.util.Objects.equals(arguments, that.arguments) && java.util.Objects.equals(expect, that.expect);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(tool);
        hash = 31 * hash + java.util.Objects.hashCode(arguments);
        hash = 31 * hash + java.util.Objects.hashCode(expect);
        return hash;
    }
    @Override public String toString() { return "ToolCallStep[tool=" + tool + ", arguments=" + arguments + ", expect=" + expect + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolCallStep> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolCallStep.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolCallStep>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolCallStep.class, "tool", ToolCallStep::tool), new dev.openallay.value.ValueSchema.Component<>(ToolCallStep.class, "arguments", ToolCallStep::arguments), new dev.openallay.value.ValueSchema.Component<>(ToolCallStep.class, "expect", ToolCallStep::expect)), arguments -> new ToolCallStep((String) arguments[0], (JsonObject) arguments[1], (TraceExpectation) arguments[2]));
        }
    }
}
