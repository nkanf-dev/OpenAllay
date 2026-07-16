package dev.tomewisp.trace.replay;

import java.util.List;

public record ReplayReport(
        String traceId,
        boolean passed,
        List<ReplayStepReport> steps,
        ReplayMetrics metrics,
        String error) {
    public ReplayReport {
        steps = List.copyOf(steps);
    }
}
