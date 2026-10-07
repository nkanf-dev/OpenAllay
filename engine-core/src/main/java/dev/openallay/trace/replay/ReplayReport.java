package dev.openallay.trace.replay;

import java.util.List;
import java.util.ArrayList;

@dev.openallay.value.ValueType(ReplayReport.ValueSchemaProvider.class)
public final class ReplayReport {
    private final String traceId;
    private final boolean passed;
    private final List<ReplayStepReport> steps;
    private final ReplayMetrics metrics;
    private final String error;
    public ReplayReport(String traceId, boolean passed, List<ReplayStepReport> steps, ReplayMetrics metrics, String error) {

        steps = dev.openallay.util.Java8Collections.listCopyOf(steps);

        this.traceId = traceId;
        this.passed = passed;
        this.steps = steps;
        this.metrics = metrics;
        this.error = error;
    }
    public String traceId() { return traceId; }
    public boolean passed() { return passed; }
    public List<ReplayStepReport> steps() { return steps; }
    public ReplayMetrics metrics() { return metrics; }
    public String error() { return error; }
public List<String> chatLines() {
        List<String> lines = new ArrayList<>();
        lines.add("TRACE " + traceId + " " + (passed ? "PASS" : "FAIL"));
        for (ReplayStepReport step : steps) {
            String tool = step.tool() == null ? "" : " tool=" + step.tool();
            lines.add("STEP " + step.index() + " " + step.type() + tool + " "
                    + (step.passed() ? "PASS" : "FAIL") + " nanos=" + step.elapsedNanos());
            if (step.actual() != null) {
                lines.add("ACTUAL " + step.actual());
            }
            if (step.expected() != null) {
                lines.add("EXPECTED " + step.expected());
            }
            if (step.assistantMessage() != null) {
                lines.add("ASSISTANT " + step.assistantMessage());
            }
            if (step.error() != null) {
                lines.add("STEP_ERROR " + step.error());
            }
        }
        lines.add("METRICS registryEntries=" + metrics.registryEntries()
                + " recipes=" + metrics.recipes()
                + " inventorySlots=" + metrics.inventorySlots()
                + " contextBytes=" + metrics.contextEstimatedSerializedBytes()
                + " contextCaptureNanos=" + metrics.contextCaptureNanos()
                + " toolResultBytes=" + metrics.toolResultSerializedBytes()
                + " totalNanos=" + metrics.totalDurationNanos());
        if (error != null) {
            lines.add("ERROR " + error);
        }
        return dev.openallay.util.Java8Collections.listCopyOf(lines);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReplayReport)) return false;
        ReplayReport that = (ReplayReport) other;
        return java.util.Objects.equals(traceId, that.traceId) && passed == that.passed && java.util.Objects.equals(steps, that.steps) && java.util.Objects.equals(metrics, that.metrics) && java.util.Objects.equals(error, that.error);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(traceId);
        hash = 31 * hash + Boolean.hashCode(passed);
        hash = 31 * hash + java.util.Objects.hashCode(steps);
        hash = 31 * hash + java.util.Objects.hashCode(metrics);
        hash = 31 * hash + java.util.Objects.hashCode(error);
        return hash;
    }
    @Override public String toString() { return "ReplayReport[traceId=" + traceId + ", passed=" + passed + ", steps=" + steps + ", metrics=" + metrics + ", error=" + error + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ReplayReport> schema() {
            return new dev.openallay.value.ValueSchema<>(ReplayReport.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ReplayReport>>asList(new dev.openallay.value.ValueSchema.Component<>(ReplayReport.class, "traceId", ReplayReport::traceId), new dev.openallay.value.ValueSchema.Component<>(ReplayReport.class, "passed", ReplayReport::passed), new dev.openallay.value.ValueSchema.Component<>(ReplayReport.class, "steps", ReplayReport::steps), new dev.openallay.value.ValueSchema.Component<>(ReplayReport.class, "metrics", ReplayReport::metrics), new dev.openallay.value.ValueSchema.Component<>(ReplayReport.class, "error", ReplayReport::error)), arguments -> new ReplayReport((String) arguments[0], (Boolean) arguments[1], (List) arguments[2], (ReplayMetrics) arguments[3], (String) arguments[4]));
        }
    }
}
