package dev.openallay.trace.replay;

import com.google.gson.JsonElement;

@dev.openallay.value.ValueType(ReplayStepReport.ValueSchemaProvider.class)
public final class ReplayStepReport {
    private final int index;
    private final String type;
    private final String tool;
    private final boolean passed;
    private final long elapsedNanos;
    private final JsonElement actual;
    private final JsonElement expected;
    private final String assistantMessage;
    private final String error;
    public ReplayStepReport(int index, String type, String tool, boolean passed, long elapsedNanos, JsonElement actual, JsonElement expected, String assistantMessage, String error) {

        if (index < 0 || elapsedNanos < 0) {
            throw new IllegalArgumentException("Replay step index and elapsed time must be non-negative");
        }
        actual = actual == null ? null : dev.openallay.json.JsonTrees.copy(actual);
        expected = expected == null ? null : dev.openallay.json.JsonTrees.copy(expected);

        this.index = index;
        this.type = type;
        this.tool = tool;
        this.passed = passed;
        this.elapsedNanos = elapsedNanos;
        this.actual = actual;
        this.expected = expected;
        this.assistantMessage = assistantMessage;
        this.error = error;
    }
    public int index() { return index; }
    public String type() { return type; }
    public String tool() { return tool; }
    public boolean passed() { return passed; }
    public long elapsedNanos() { return elapsedNanos; }
    public String assistantMessage() { return assistantMessage; }
    public String error() { return error; }

    public JsonElement actual() {
        return actual == null ? null : dev.openallay.json.JsonTrees.copy(actual);
    }

    public JsonElement expected() {
        return expected == null ? null : dev.openallay.json.JsonTrees.copy(expected);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReplayStepReport)) return false;
        ReplayStepReport that = (ReplayStepReport) other;
        return index == that.index && java.util.Objects.equals(type, that.type) && java.util.Objects.equals(tool, that.tool) && passed == that.passed && elapsedNanos == that.elapsedNanos && java.util.Objects.equals(actual, that.actual) && java.util.Objects.equals(expected, that.expected) && java.util.Objects.equals(assistantMessage, that.assistantMessage) && java.util.Objects.equals(error, that.error);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(index);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(tool);
        hash = 31 * hash + Boolean.hashCode(passed);
        hash = 31 * hash + Long.hashCode(elapsedNanos);
        hash = 31 * hash + java.util.Objects.hashCode(actual);
        hash = 31 * hash + java.util.Objects.hashCode(expected);
        hash = 31 * hash + java.util.Objects.hashCode(assistantMessage);
        hash = 31 * hash + java.util.Objects.hashCode(error);
        return hash;
    }
    @Override public String toString() { return "ReplayStepReport[index=" + index + ", type=" + type + ", tool=" + tool + ", passed=" + passed + ", elapsedNanos=" + elapsedNanos + ", actual=" + actual + ", expected=" + expected + ", assistantMessage=" + assistantMessage + ", error=" + error + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ReplayStepReport> schema() {
            return new dev.openallay.value.ValueSchema<>(ReplayStepReport.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ReplayStepReport>>asList(new dev.openallay.value.ValueSchema.Component<>(ReplayStepReport.class, "index", ReplayStepReport::index), new dev.openallay.value.ValueSchema.Component<>(ReplayStepReport.class, "type", ReplayStepReport::type), new dev.openallay.value.ValueSchema.Component<>(ReplayStepReport.class, "tool", ReplayStepReport::tool), new dev.openallay.value.ValueSchema.Component<>(ReplayStepReport.class, "passed", ReplayStepReport::passed), new dev.openallay.value.ValueSchema.Component<>(ReplayStepReport.class, "elapsedNanos", ReplayStepReport::elapsedNanos), new dev.openallay.value.ValueSchema.Component<>(ReplayStepReport.class, "actual", ReplayStepReport::actual), new dev.openallay.value.ValueSchema.Component<>(ReplayStepReport.class, "expected", ReplayStepReport::expected), new dev.openallay.value.ValueSchema.Component<>(ReplayStepReport.class, "assistantMessage", ReplayStepReport::assistantMessage), new dev.openallay.value.ValueSchema.Component<>(ReplayStepReport.class, "error", ReplayStepReport::error)), arguments -> new ReplayStepReport((Integer) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3], (Long) arguments[4], (JsonElement) arguments[5], (JsonElement) arguments[6], (String) arguments[7], (String) arguments[8]));
        }
    }
}
