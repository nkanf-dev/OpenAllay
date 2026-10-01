package dev.openallay.benchmark;

import java.util.List;

public record BenchmarkReport(
        List<CaseReport> cases) {
    public BenchmarkReport {
        cases = List.copyOf(cases);
    }

    public record CaseReport(
            String caseId,
            int attempts,
            int successes,
            double successProbability,
            double averageModelTurns,
            double medianModelTurns,
            double averageToolCalls,
            double medianToolCalls,
            List<AttemptReport> attemptReports) {
        public CaseReport {
            caseId = require(caseId);
            attemptReports = List.copyOf(attemptReports);
            if (attempts != attemptReports.size()) {
                throw new IllegalArgumentException(
                        "attempt count must match retained attempt reports");
            }
            if (successes != attemptReports.stream()
                    .filter(AttemptReport::success)
                    .count()) {
                throw new IllegalArgumentException(
                        "success count must match retained attempt reports");
            }
        }

        public List<BenchmarkMetrics> metrics() {
            return attemptReports.stream().map(AttemptReport::metrics).toList();
        }

        public List<String> diagnostics() {
            return attemptReports.stream().map(AttemptReport::diagnostic).toList();
        }
    }

    public record AttemptReport(
            int attempt,
            boolean success,
            FailureKind failureKind,
            String diagnostic,
            BenchmarkMetrics metrics) {
        public AttemptReport {
            if (attempt < 1) {
                throw new IllegalArgumentException("attempt must be positive");
            }
            java.util.Objects.requireNonNull(failureKind, "failureKind");
            diagnostic = diagnostic == null ? "" : diagnostic;
            java.util.Objects.requireNonNull(metrics, "metrics");
            if (success != metrics.success()) {
                throw new IllegalArgumentException(
                        "attempt success must match retained metrics");
            }
            if (success != (failureKind == FailureKind.NONE)) {
                throw new IllegalArgumentException(
                        "successful attempts require NONE and failures require a failure kind");
            }
            if (success && !diagnostic.isEmpty()) {
                throw new IllegalArgumentException(
                        "successful attempts cannot retain a failure diagnostic");
            }
        }
    }

    public enum FailureKind {
        NONE,
        RUNTIME_TERMINAL,
        VERIFICATION,
        MODEL_TURN_BUDGET
    }

    private static String require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value;
    }
}
