package dev.openallay.benchmark;

import java.util.List;

@dev.openallay.value.ValueType(BenchmarkReport.ValueSchemaProvider.class)
public final class BenchmarkReport {
    private final List<CaseReport> cases;
    public BenchmarkReport(List<CaseReport> cases) {

        cases = List.copyOf(cases);

        this.cases = cases;
    }
    public List<CaseReport> cases() { return cases; }
@dev.openallay.value.ValueType(CaseReport.ValueSchemaProvider.class)
public static final class CaseReport {
    private final String caseId;
    private final int attempts;
    private final int successes;
    private final double successProbability;
    private final double averageModelTurns;
    private final double medianModelTurns;
    private final double averageToolCalls;
    private final double medianToolCalls;
    private final List<AttemptReport> attemptReports;
    public CaseReport(String caseId, int attempts, int successes, double successProbability, double averageModelTurns, double medianModelTurns, double averageToolCalls, double medianToolCalls, List<AttemptReport> attemptReports) {

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

        this.caseId = caseId;
        this.attempts = attempts;
        this.successes = successes;
        this.successProbability = successProbability;
        this.averageModelTurns = averageModelTurns;
        this.medianModelTurns = medianModelTurns;
        this.averageToolCalls = averageToolCalls;
        this.medianToolCalls = medianToolCalls;
        this.attemptReports = attemptReports;
    }
    public String caseId() { return caseId; }
    public int attempts() { return attempts; }
    public int successes() { return successes; }
    public double successProbability() { return successProbability; }
    public double averageModelTurns() { return averageModelTurns; }
    public double medianModelTurns() { return medianModelTurns; }
    public double averageToolCalls() { return averageToolCalls; }
    public double medianToolCalls() { return medianToolCalls; }
    public List<AttemptReport> attemptReports() { return attemptReports; }
public List<BenchmarkMetrics> metrics() {
            return attemptReports.stream().map(AttemptReport::metrics).toList();
        }
public List<String> diagnostics() {
            return attemptReports.stream().map(AttemptReport::diagnostic).toList();
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CaseReport)) return false;
        CaseReport that = (CaseReport) other;
        return java.util.Objects.equals(caseId, that.caseId) && attempts == that.attempts && successes == that.successes && Double.compare(successProbability, that.successProbability) == 0 && Double.compare(averageModelTurns, that.averageModelTurns) == 0 && Double.compare(medianModelTurns, that.medianModelTurns) == 0 && Double.compare(averageToolCalls, that.averageToolCalls) == 0 && Double.compare(medianToolCalls, that.medianToolCalls) == 0 && java.util.Objects.equals(attemptReports, that.attemptReports);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(caseId);
        hash = 31 * hash + Integer.hashCode(attempts);
        hash = 31 * hash + Integer.hashCode(successes);
        hash = 31 * hash + Double.hashCode(successProbability);
        hash = 31 * hash + Double.hashCode(averageModelTurns);
        hash = 31 * hash + Double.hashCode(medianModelTurns);
        hash = 31 * hash + Double.hashCode(averageToolCalls);
        hash = 31 * hash + Double.hashCode(medianToolCalls);
        hash = 31 * hash + java.util.Objects.hashCode(attemptReports);
        return hash;
    }
    @Override public String toString() { return "CaseReport[caseId=" + caseId + ", attempts=" + attempts + ", successes=" + successes + ", successProbability=" + successProbability + ", averageModelTurns=" + averageModelTurns + ", medianModelTurns=" + medianModelTurns + ", averageToolCalls=" + averageToolCalls + ", medianToolCalls=" + medianToolCalls + ", attemptReports=" + attemptReports + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CaseReport> schema() {
            return new dev.openallay.value.ValueSchema<>(CaseReport.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CaseReport>>asList(new dev.openallay.value.ValueSchema.Component<>(CaseReport.class, "caseId", CaseReport::caseId), new dev.openallay.value.ValueSchema.Component<>(CaseReport.class, "attempts", CaseReport::attempts), new dev.openallay.value.ValueSchema.Component<>(CaseReport.class, "successes", CaseReport::successes), new dev.openallay.value.ValueSchema.Component<>(CaseReport.class, "successProbability", CaseReport::successProbability), new dev.openallay.value.ValueSchema.Component<>(CaseReport.class, "averageModelTurns", CaseReport::averageModelTurns), new dev.openallay.value.ValueSchema.Component<>(CaseReport.class, "medianModelTurns", CaseReport::medianModelTurns), new dev.openallay.value.ValueSchema.Component<>(CaseReport.class, "averageToolCalls", CaseReport::averageToolCalls), new dev.openallay.value.ValueSchema.Component<>(CaseReport.class, "medianToolCalls", CaseReport::medianToolCalls), new dev.openallay.value.ValueSchema.Component<>(CaseReport.class, "attemptReports", CaseReport::attemptReports)), arguments -> new CaseReport((String) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Double) arguments[3], (Double) arguments[4], (Double) arguments[5], (Double) arguments[6], (Double) arguments[7], (List) arguments[8]));
        }
    }
}
@dev.openallay.value.ValueType(AttemptReport.ValueSchemaProvider.class)
public static final class AttemptReport {
    private final int attempt;
    private final boolean success;
    private final FailureKind failureKind;
    private final String diagnostic;
    private final BenchmarkMetrics metrics;
    public AttemptReport(int attempt, boolean success, FailureKind failureKind, String diagnostic, BenchmarkMetrics metrics) {

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

        this.attempt = attempt;
        this.success = success;
        this.failureKind = failureKind;
        this.diagnostic = diagnostic;
        this.metrics = metrics;
    }
    public int attempt() { return attempt; }
    public boolean success() { return success; }
    public FailureKind failureKind() { return failureKind; }
    public String diagnostic() { return diagnostic; }
    public BenchmarkMetrics metrics() { return metrics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AttemptReport)) return false;
        AttemptReport that = (AttemptReport) other;
        return attempt == that.attempt && success == that.success && java.util.Objects.equals(failureKind, that.failureKind) && java.util.Objects.equals(diagnostic, that.diagnostic) && java.util.Objects.equals(metrics, that.metrics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(attempt);
        hash = 31 * hash + Boolean.hashCode(success);
        hash = 31 * hash + java.util.Objects.hashCode(failureKind);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        hash = 31 * hash + java.util.Objects.hashCode(metrics);
        return hash;
    }
    @Override public String toString() { return "AttemptReport[attempt=" + attempt + ", success=" + success + ", failureKind=" + failureKind + ", diagnostic=" + diagnostic + ", metrics=" + metrics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<AttemptReport> schema() {
            return new dev.openallay.value.ValueSchema<>(AttemptReport.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<AttemptReport>>asList(new dev.openallay.value.ValueSchema.Component<>(AttemptReport.class, "attempt", AttemptReport::attempt), new dev.openallay.value.ValueSchema.Component<>(AttemptReport.class, "success", AttemptReport::success), new dev.openallay.value.ValueSchema.Component<>(AttemptReport.class, "failureKind", AttemptReport::failureKind), new dev.openallay.value.ValueSchema.Component<>(AttemptReport.class, "diagnostic", AttemptReport::diagnostic), new dev.openallay.value.ValueSchema.Component<>(AttemptReport.class, "metrics", AttemptReport::metrics)), arguments -> new AttemptReport((Integer) arguments[0], (Boolean) arguments[1], (FailureKind) arguments[2], (String) arguments[3], (BenchmarkMetrics) arguments[4]));
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BenchmarkReport)) return false;
        BenchmarkReport that = (BenchmarkReport) other;
        return java.util.Objects.equals(cases, that.cases);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(cases);
        return hash;
    }
    @Override public String toString() { return "BenchmarkReport[cases=" + cases + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BenchmarkReport> schema() {
            return new dev.openallay.value.ValueSchema<>(BenchmarkReport.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BenchmarkReport>>asList(new dev.openallay.value.ValueSchema.Component<>(BenchmarkReport.class, "cases", BenchmarkReport::cases)), arguments -> new BenchmarkReport((List) arguments[0]));
        }
    }
}
