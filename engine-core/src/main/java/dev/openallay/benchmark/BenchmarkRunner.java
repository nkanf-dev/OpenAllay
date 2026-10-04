package dev.openallay.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes repeatable cases and scores rounds/calls; wall-clock latency is intentionally absent. */
public final class BenchmarkRunner {
    @FunctionalInterface
    public interface Executor {
        BenchmarkOutcome execute(BenchmarkCase testCase, int attempt);
    }

    private final BenchmarkVerifier verifier;

    public BenchmarkRunner(BenchmarkVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    public BenchmarkReport run(
            List<BenchmarkCase> cases,
            Executor executor) {
        Objects.requireNonNull(executor, "executor");
        ArrayList<BenchmarkReport.CaseReport> reports = new ArrayList<>();
        for (BenchmarkCase testCase : List.copyOf(cases)) {
            ArrayList<BenchmarkReport.AttemptReport> attemptReports = new ArrayList<>();
            int successes = 0;
            long modelTurns = 0;
            long toolCalls = 0;
            for (int attempt = 1; attempt <= testCase.attempts(); attempt++) {
                BenchmarkOutcome outcome = Objects.requireNonNull(
                        executor.execute(testCase, attempt), "benchmark outcome");
                BenchmarkVerifier.Verification verification =
                        verifier.verify(testCase, outcome);
                BenchmarkReport.FailureKind failureKind =
                        failureKind(testCase, outcome, verification);
                boolean success = failureKind == BenchmarkReport.FailureKind.NONE;
                BenchmarkMetrics measured = withSuccess(outcome.metrics(), success);
                attemptReports.add(new BenchmarkReport.AttemptReport(
                        attempt,
                        success,
                        failureKind,
                        diagnostic(failureKind, outcome, verification),
                        measured));
                modelTurns += measured.modelTurns();
                toolCalls += measured.toolCalls();
                if (success) {
                    successes++;
                }
            }
            int attempts = testCase.attempts();
            reports.add(new BenchmarkReport.CaseReport(
                    testCase.id(),
                    attempts,
                    successes,
                    successes / (double) attempts,
                    modelTurns / (double) attempts,
                    median(attemptReports.stream()
                            .map(BenchmarkReport.AttemptReport::metrics)
                            .map(BenchmarkMetrics::modelTurns)
                            .toList()),
                    toolCalls / (double) attempts,
                    median(attemptReports.stream()
                            .map(BenchmarkReport.AttemptReport::metrics)
                            .map(BenchmarkMetrics::toolCalls)
                            .toList()),
                    attemptReports));
        }
        return new BenchmarkReport(reports);
    }

    private static BenchmarkReport.FailureKind failureKind(
            BenchmarkCase testCase,
            BenchmarkOutcome outcome,
            BenchmarkVerifier.Verification verification) {
        if (!outcome.metrics().success()) {
            return BenchmarkReport.FailureKind.RUNTIME_TERMINAL;
        }
        if (!verification.passed()) {
            return BenchmarkReport.FailureKind.VERIFICATION;
        }
        if (outcome.metrics().modelTurns() > testCase.maxModelTurns()) {
            return BenchmarkReport.FailureKind.MODEL_TURN_BUDGET;
        }
        return BenchmarkReport.FailureKind.NONE;
    }

    private static String diagnostic(
            BenchmarkReport.FailureKind failureKind,
            BenchmarkOutcome outcome,
            BenchmarkVerifier.Verification verification) {
        return switch (failureKind) {
            case NONE -> "";
            case RUNTIME_TERMINAL -> outcome.metrics().terminalCode().isBlank()
                    ? "runtime terminal failure"
                    : "runtime terminal: " + outcome.metrics().terminalCode();
            case VERIFICATION -> verification.diagnostic();
            case MODEL_TURN_BUDGET -> "model turn budget exceeded";
        };
    }

    private static double median(List<Integer> values) {
        List<Integer> ordered = values.stream().sorted().toList();
        int middle = ordered.size() / 2;
        return ordered.size() % 2 == 1
                ? ordered.get(middle)
                : (ordered.get(middle - 1) + ordered.get(middle)) / 2.0D;
    }

    private static BenchmarkMetrics withSuccess(BenchmarkMetrics metrics, boolean success) {
        return new BenchmarkMetrics(
                success,
                metrics.modelTurns(),
                metrics.toolCalls(),
                metrics.javascriptCalls(),
                metrics.skillLoads(),
                metrics.skillReloads(),
                metrics.duplicateSkillLoads(),
                metrics.invalidCalls(),
                metrics.correctedCalls(),
                metrics.terminalCode());
    }
}
